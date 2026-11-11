package uzeduai.be.instagram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.dto.OauthCallbackOutcome;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramOauthState;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramOauthStateRepository;
import uzeduai.be.instagram.service.InstagramOauthService;
import uzeduai.be.instagram.service.InstagramSyncService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;
import uzeduai.be.user.entity.User;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstagramOauthServiceUnitTest {

    @Mock InstagramProperties properties;
    @Mock InstagramOauthStateRepository oauthStateRepository;
    @Mock InstagramAccountRepository accountRepository;
    @Mock InstagramGraphClient graphClient;
    @Mock TenantMembershipService tenantMembershipService;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock InstagramSyncService syncService;
    @Mock AccessService accessService;

    private InstagramOauthService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InstagramOauthService(
                properties,
                oauthStateRepository,
                accountRepository,
                graphClient,
                tenantMembershipService,
                workspaceRepository,
                syncService,
                accessService
        );
        when(properties.isEnabled()).thenReturn(true);
    }

    @Test
    void handleCallbackConnectsSingleDiscoveredAccount() {
        InstagramOauthState state = usableState();
        when(oauthStateRepository.findByState("st")).thenReturn(Optional.of(state));
        when(graphClient.exchangeCode("code")).thenReturn(
                new InstagramGraphClient.TokenExchangeResult("tok", 3600L));
        when(graphClient.discoverInstagramAccounts("tok")).thenReturn(List.of(
                new InstagramGraphClient.IgUserProfile("ig1", "one", "BUSINESS")
        ));
        when(accountRepository.findByTenantIdAndIgUserId(tenantId, "ig1")).thenReturn(Optional.empty());
        when(accountRepository.save(any(InstagramAccount.class))).thenAnswer(inv -> {
            InstagramAccount a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        OauthCallbackOutcome outcome = service.handleCallback("code", "st");

        assertThat(outcome.needsPick()).isFalse();
        assertThat(outcome.accountId()).isNotNull();
        verify(syncService).enqueueHistoricalSync(any(InstagramAccount.class));
    }

    @Test
    void handleCallbackCreatesPickSessionWhenMultipleAccounts() {
        InstagramOauthState state = usableState();
        when(oauthStateRepository.findByState("st")).thenReturn(Optional.of(state));
        when(graphClient.exchangeCode("code")).thenReturn(
                new InstagramGraphClient.TokenExchangeResult("tok", 3600L));
        when(graphClient.discoverInstagramAccounts("tok")).thenReturn(List.of(
                new InstagramGraphClient.IgUserProfile("ig1", "one", "BUSINESS"),
                new InstagramGraphClient.IgUserProfile("ig2", "two", "BUSINESS", "page-tok")
        ));

        OauthCallbackOutcome outcome = service.handleCallback("code", "st");

        assertThat(outcome.needsPick()).isTrue();
        assertThat(outcome.pickId()).isNotBlank();
        verify(accountRepository, never()).save(any());
        verify(syncService, never()).enqueueHistoricalSync(any());

        CustomUserDetails principal = principal();
        var session = service.getPickSession(outcome.pickId(), principal);
        assertThat(session.candidates()).hasSize(2);

        when(accountRepository.findByTenantIdAndIgUserId(tenantId, "ig2")).thenReturn(Optional.empty());
        when(accountRepository.save(any(InstagramAccount.class))).thenAnswer(inv -> {
            InstagramAccount a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        InstagramAccount connected = service.confirmPick(outcome.pickId(), "ig2", principal);
        assertThat(connected.getIgUserId()).isEqualTo("ig2");
        assertThat(connected.getAccessToken()).isEqualTo("page-tok");
        assertThat(connected.getStatus()).isEqualTo(InstagramAccountStatus.ACTIVE);
        verify(syncService).enqueueHistoricalSync(any(InstagramAccount.class));
    }

    @Test
    void handleCallbackFailsWhenDiscoveryEmpty() {
        InstagramOauthState state = usableState();
        when(oauthStateRepository.findByState("st")).thenReturn(Optional.of(state));
        when(graphClient.exchangeCode("code")).thenReturn(
                new InstagramGraphClient.TokenExchangeResult("tok", null));
        when(graphClient.discoverInstagramAccounts("tok")).thenReturn(List.of());

        assertThatThrownBy(() -> service.handleCallback("code", "st"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getMessageKey())
                .isEqualTo(MessageKeys.INSTAGRAM_ACCOUNT_DISCOVERY_FAILED);
    }

    @Test
    void startRequiresInstagramPageAccess() {
        CustomUserDetails principal = principal();
        doNothing().when(accessService).requirePage(principal, "INSTAGRAM");
        when(workspaceRepository.findByIdAndTenantId(workspaceId, tenantId))
                .thenReturn(Optional.of(new Workspace()));
        when(properties.isUnifiedLogin()).thenReturn(false);
        when(properties.getAuthorizeBaseUrl()).thenReturn("https://api.instagram.com/oauth/authorize");
        when(properties.getAppId()).thenReturn("app");
        when(properties.getRedirectUri()).thenReturn("https://example.com/cb");
        when(properties.getScopes()).thenReturn("instagram_business_basic");
        when(properties.getOauthStateTtlMinutes()).thenReturn(10);
        when(oauthStateRepository.save(any(InstagramOauthState.class))).thenAnswer(inv -> inv.getArgument(0));

        var start = service.start(principal);
        assertThat(start.authorizationUrl()).contains("client_id=app");
        verify(accessService).requirePage(principal, "INSTAGRAM");
    }

    private InstagramOauthState usableState() {
        InstagramOauthState state = new InstagramOauthState();
        state.setState("st");
        state.setUserId(userId);
        state.setTenantId(tenantId);
        state.setWorkspaceId(workspaceId);
        state.setExpiresAt(Instant.now().plusSeconds(600));
        return state;
    }

    private CustomUserDetails principal() {
        User user = new User();
        user.setId(userId);
        user.setEmail("admin@local.dev");
        user.setPasswordHash("x");
        user.setFullName("Admin");
        user.setEnabled(true);
        return new CustomUserDetails(user, tenantId, workspaceId, TenantRole.OWNER);
    }
}
