package uzeduai.be.instagram.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.dto.InstagramOauthStartResponse;
import uzeduai.be.instagram.dto.OauthCallbackOutcome;
import uzeduai.be.instagram.dto.OauthPickCandidateResponse;
import uzeduai.be.instagram.dto.OauthPickSessionResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramOauthState;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramOauthStateRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InstagramOauthService {

    private static final Logger log = LoggerFactory.getLogger(InstagramOauthService.class);
    private static final long PICK_TTL_SECONDS = 600L;

    private final InstagramProperties properties;
    private final InstagramOauthStateRepository oauthStateRepository;
    private final InstagramAccountRepository accountRepository;
    private final InstagramGraphClient graphClient;
    private final TenantMembershipService tenantMembershipService;
    private final WorkspaceRepository workspaceRepository;
    private final InstagramSyncService syncService;
    private final AccessService accessService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, PickSession> pickSessions = new ConcurrentHashMap<>();

    public InstagramOauthService(
            InstagramProperties properties,
            InstagramOauthStateRepository oauthStateRepository,
            InstagramAccountRepository accountRepository,
            InstagramGraphClient graphClient,
            TenantMembershipService tenantMembershipService,
            WorkspaceRepository workspaceRepository,
            @Lazy InstagramSyncService syncService,
            AccessService accessService
    ) {
        this.properties = properties;
        this.oauthStateRepository = oauthStateRepository;
        this.accountRepository = accountRepository;
        this.graphClient = graphClient;
        this.tenantMembershipService = tenantMembershipService;
        this.workspaceRepository = workspaceRepository;
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @Transactional
    public InstagramOauthStartResponse start(CustomUserDetails principal) {
        requireEnabled();
        accessService.requirePage(principal, "INSTAGRAM");
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        workspaceRepository.findByIdAndTenantId(principal.getWorkspaceId(), principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED));

        String stateValue = generateState();
        InstagramOauthState state = new InstagramOauthState();
        state.setState(stateValue);
        state.setUserId(principal.getId());
        state.setTenantId(principal.getTenantId());
        state.setWorkspaceId(principal.getWorkspaceId());
        state.setExpiresAt(Instant.now().plusSeconds(properties.getOauthStateTtlMinutes() * 60L));
        oauthStateRepository.save(state);

        String url;
        if (properties.isUnifiedLogin()) {
            // With config_id, scopes come from the Meta Login Configuration — do not append scope=
            url = UriComponentsBuilder
                    .fromUriString(properties.getFacebookAuthorizeBaseUrl()
                            + "/" + properties.getGraphVersion() + "/dialog/oauth")
                    .queryParam("client_id", properties.getAppId())
                    .queryParam("config_id", properties.getConfigId())
                    .queryParam("redirect_uri", properties.getRedirectUri())
                    .queryParam("response_type", "code")
                    .queryParam("override_default_response_type", "true")
                    .queryParam("state", stateValue)
                    .build(true)
                    .toUriString();
        } else {
            url = UriComponentsBuilder
                    .fromUriString(properties.getAuthorizeBaseUrl())
                    .queryParam("client_id", properties.getAppId())
                    .queryParam("redirect_uri", properties.getRedirectUri())
                    .queryParam("scope", properties.getScopes())
                    .queryParam("response_type", "code")
                    .queryParam("state", stateValue)
                    .build(true)
                    .toUriString();
        }

        return new InstagramOauthStartResponse(url, stateValue);
    }

    @Transactional
    public OauthCallbackOutcome handleCallback(String code, String stateValue) {
        requireEnabled();
        InstagramOauthState state = oauthStateRepository.findByState(stateValue)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_OAUTH_STATE_INVALID));
        if (!state.isUsable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_OAUTH_STATE_INVALID);
        }
        state.setConsumedAt(Instant.now());
        oauthStateRepository.save(state);

        tenantMembershipService.requireMembership(state.getTenantId(), state.getUserId());

        var tokens = graphClient.exchangeCode(code);
        List<InstagramGraphClient.IgUserProfile> profiles;
        try {
            profiles = graphClient.discoverInstagramAccounts(tokens.accessToken());
        } catch (RuntimeException ex) {
            log.warn("Instagram discovery threw after OAuth: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSTAGRAM_ACCOUNT_DISCOVERY_FAILED);
        }
        if (profiles == null || profiles.isEmpty()) {
            log.warn(
                    "Instagram discovery returned 0 accounts (tenant={}, user={}). "
                            + "Check Login Configuration assets, Page↔IG link, and dialog selection.",
                    state.getTenantId(),
                    state.getUserId()
            );
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSTAGRAM_ACCOUNT_DISCOVERY_FAILED);
        }

        if (profiles.size() == 1) {
            InstagramAccount account = persistAccount(
                    state,
                    profiles.getFirst(),
                    tokens.accessToken(),
                    tokens.expiresInSeconds()
            );
            syncService.enqueueHistoricalSync(account);
            return OauthCallbackOutcome.connected(account.getId());
        }

        String pickId = generateState();
        pickSessions.put(pickId, new PickSession(
                pickId,
                state.getUserId(),
                state.getTenantId(),
                state.getWorkspaceId(),
                tokens.accessToken(),
                tokens.expiresInSeconds(),
                List.copyOf(profiles),
                Instant.now().plusSeconds(PICK_TTL_SECONDS)
        ));
        purgeExpiredPickSessions();
        log.info("OAuth multi-account pick required: pickId={} count={}", pickId, profiles.size());
        return OauthCallbackOutcome.needsPick(pickId);
    }

    @Transactional(readOnly = true)
    public OauthPickSessionResponse getPickSession(String pickId, CustomUserDetails principal) {
        requireEnabled();
        accessService.requirePage(principal, "INSTAGRAM");
        PickSession session = requirePickSession(pickId, principal);
        List<OauthPickCandidateResponse> candidates = session.profiles().stream()
                .map(p -> new OauthPickCandidateResponse(p.id(), p.username(), p.accountType()))
                .toList();
        return new OauthPickSessionResponse(pickId, candidates);
    }

    @Transactional
    public InstagramAccount confirmPick(String pickId, String igUserId, CustomUserDetails principal) {
        requireEnabled();
        accessService.requirePage(principal, "INSTAGRAM");
        PickSession session = requirePickSession(pickId, principal);
        InstagramGraphClient.IgUserProfile profile = session.profiles().stream()
                .filter(p -> igUserId.equals(p.id()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_OAUTH_PICK_INVALID));

        InstagramOauthState fakeState = new InstagramOauthState();
        fakeState.setUserId(session.userId());
        fakeState.setTenantId(session.tenantId());
        fakeState.setWorkspaceId(session.workspaceId());

        InstagramAccount account = persistAccount(
                fakeState,
                profile,
                session.userAccessToken(),
                session.expiresInSeconds()
        );
        pickSessions.remove(pickId);
        syncService.enqueueHistoricalSync(account);
        return account;
    }

    private PickSession requirePickSession(String pickId, CustomUserDetails principal) {
        purgeExpiredPickSessions();
        PickSession session = pickSessions.get(pickId);
        if (session == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_OAUTH_PICK_EXPIRED);
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            pickSessions.remove(pickId);
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_OAUTH_PICK_EXPIRED);
        }
        if (!session.userId().equals(principal.getId())
                || !session.tenantId().equals(principal.getTenantId())
                || !session.workspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.SECURITY_FORBIDDEN);
        }
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        return session;
    }

    private InstagramAccount persistAccount(
            InstagramOauthState state,
            InstagramGraphClient.IgUserProfile profile,
            String userAccessToken,
            Long expiresInSeconds
    ) {
        if (profile.id() == null || profile.id().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSTAGRAM_ACCOUNT_DISCOVERY_FAILED);
        }
        InstagramAccount account = accountRepository
                .findByTenantIdAndIgUserId(state.getTenantId(), profile.id())
                .orElseGet(InstagramAccount::new);
        account.setTenantId(state.getTenantId());
        account.setWorkspaceId(state.getWorkspaceId());
        account.setIgUserId(profile.id());
        account.setUsername(profile.username());
        account.setAccountType(profile.accountType());
        String accessToken = profile.accessToken() != null && !profile.accessToken().isBlank()
                ? profile.accessToken()
                : userAccessToken;
        account.setAccessToken(accessToken);
        if (expiresInSeconds != null && (profile.accessToken() == null || profile.accessToken().isBlank())) {
            account.setTokenExpiresAt(Instant.now().plusSeconds(expiresInSeconds));
        } else if (profile.accessToken() != null && !profile.accessToken().isBlank()) {
            // Page tokens from /me/accounts typically do not include expires_in here.
            account.setTokenExpiresAt(null);
        }
        account.setScopes(properties.isUnifiedLogin()
                ? "unified:" + properties.getConfigId()
                : properties.getScopes());
        account.setStatus(InstagramAccountStatus.ACTIVE);
        account.setCreatedByUserId(state.getUserId());
        return accountRepository.save(account);
    }

    private void purgeExpiredPickSessions() {
        Instant now = Instant.now();
        pickSessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.INSTAGRAM_DISABLED);
        }
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record PickSession(
            String pickId,
            UUID userId,
            UUID tenantId,
            UUID workspaceId,
            String userAccessToken,
            Long expiresInSeconds,
            List<InstagramGraphClient.IgUserProfile> profiles,
            Instant expiresAt
    ) {
    }
}
