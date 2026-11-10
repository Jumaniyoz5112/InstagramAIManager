package uzeduai.be.instagram.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.dto.InstagramAccountResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.service.InstagramAccountService;
import uzeduai.be.instagram.service.InstagramSyncService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

/**
 * Dev-only helper to seed an IG Business account from a Meta System User token
 * when same-portfolio OAuth discovery returns empty assigned accounts.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/instagram/dev")
public class InstagramDevSeedController {

    private final InstagramAccountRepository accountRepository;
    private final InstagramAccountService accountService;
    private final InstagramSyncService syncService;
    private final AccessService accessService;
    private final TenantMembershipService tenantMembershipService;

    public InstagramDevSeedController(
            InstagramAccountRepository accountRepository,
            InstagramAccountService accountService,
            InstagramSyncService syncService,
            AccessService accessService,
            TenantMembershipService tenantMembershipService
    ) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.syncService = syncService;
        this.accessService = accessService;
        this.tenantMembershipService = tenantMembershipService;
    }

    public record SeedRequest(
            @NotBlank String igUserId,
            @NotBlank String username,
            String accountType,
            @NotBlank String accessToken,
            String scopes
    ) {
    }

    @PostMapping("/seed-account")
    public ResponseEntity<ApiResponse<InstagramAccountResponse>> seed(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody SeedRequest request
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new uzeduai.be.common.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    uzeduai.be.common.i18n.MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());

        InstagramAccount account = accountRepository
                .findByTenantIdAndIgUserId(principal.getTenantId(), request.igUserId())
                .orElseGet(InstagramAccount::new);
        account.setTenantId(principal.getTenantId());
        account.setWorkspaceId(principal.getWorkspaceId());
        account.setIgUserId(request.igUserId().trim());
        account.setUsername(request.username().trim());
        account.setAccountType(request.accountType() == null || request.accountType().isBlank()
                ? "BUSINESS"
                : request.accountType().trim());
        account.setAccessToken(request.accessToken().trim());
        account.setTokenExpiresAt(null);
        account.setScopes(request.scopes() == null || request.scopes().isBlank()
                ? "dev:system_user"
                : request.scopes().trim());
        account.setStatus(InstagramAccountStatus.ACTIVE);
        account.setCreatedByUserId(principal.getId());
        account = accountRepository.saveAndFlush(account);

        // Round-trip decrypt check — catches crypto init / ciphertext issues before sync.
        InstagramAccount reloaded = accountRepository.findById(account.getId())
                .orElseThrow();
        String stored = reloaded.getAccessToken();
        if (stored == null || stored.length() < 40 || !stored.startsWith("EAA")) {
            throw new IllegalStateException(
                    "Encrypted token round-trip failed (len="
                            + (stored == null ? -1 : stored.length()) + ")");
        }

        syncService.enqueueHistoricalSync(reloaded);
        return ResponseEntity.ok(ApiResponse.ok(accountService.toResponse(reloaded)));
    }
}
