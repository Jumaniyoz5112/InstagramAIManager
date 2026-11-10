package uzeduai.be.instagram.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.domain.SyncJobType;
import uzeduai.be.instagram.dto.InstagramOauthStartResponse;
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
import java.util.UUID;

@Service
public class InstagramOauthService {

    private final InstagramProperties properties;
    private final InstagramOauthStateRepository oauthStateRepository;
    private final InstagramAccountRepository accountRepository;
    private final InstagramGraphClient graphClient;
    private final TenantMembershipService tenantMembershipService;
    private final WorkspaceRepository workspaceRepository;
    private final InstagramSyncService syncService;
    private final SecureRandom secureRandom = new SecureRandom();

    public InstagramOauthService(
            InstagramProperties properties,
            InstagramOauthStateRepository oauthStateRepository,
            InstagramAccountRepository accountRepository,
            InstagramGraphClient graphClient,
            TenantMembershipService tenantMembershipService,
            WorkspaceRepository workspaceRepository,
            @Lazy InstagramSyncService syncService
    ) {
        this.properties = properties;
        this.oauthStateRepository = oauthStateRepository;
        this.accountRepository = accountRepository;
        this.graphClient = graphClient;
        this.tenantMembershipService = tenantMembershipService;
        this.workspaceRepository = workspaceRepository;
        this.syncService = syncService;
    }

    @Transactional
    public InstagramOauthStartResponse start(CustomUserDetails principal) {
        requireEnabled();
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
            // (extra scopes can make Facebook Login return "Feature Unavailable").
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
    public InstagramAccount handleCallback(String code, String stateValue) {
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
        InstagramGraphClient.IgUserProfile profile;
        try {
            profile = graphClient.fetchMe(tokens.accessToken());
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSTAGRAM_ACCOUNT_DISCOVERY_FAILED);
        }
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
                : tokens.accessToken();
        account.setAccessToken(accessToken);
        if (tokens.expiresInSeconds() != null && profile.accessToken() == null) {
            account.setTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()));
        } else if (profile.accessToken() != null) {
            // Page tokens from /me/accounts typically do not include expires_in here.
            account.setTokenExpiresAt(null);
        }
        account.setScopes(properties.isUnifiedLogin()
                ? "unified:" + properties.getConfigId()
                : properties.getScopes());
        account.setStatus(InstagramAccountStatus.ACTIVE);
        account.setCreatedByUserId(state.getUserId());
        account = accountRepository.save(account);

        syncService.enqueueHistoricalSync(account);
        return account;
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
}
