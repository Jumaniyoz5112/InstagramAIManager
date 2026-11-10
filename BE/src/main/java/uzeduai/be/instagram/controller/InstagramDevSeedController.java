package uzeduai.be.instagram.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.dto.InstagramAccountResponse;
import uzeduai.be.instagram.dto.InstagramPermissionProbeResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.service.InstagramAccountService;
import uzeduai.be.instagram.service.InstagramSyncService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final InstagramGraphClient graphClient;
    private final AccessService accessService;
    private final TenantMembershipService tenantMembershipService;

    public InstagramDevSeedController(
            InstagramAccountRepository accountRepository,
            InstagramAccountService accountService,
            InstagramSyncService syncService,
            InstagramGraphClient graphClient,
            AccessService accessService,
            TenantMembershipService tenantMembershipService
    ) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.syncService = syncService;
        this.graphClient = graphClient;
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

    /**
     * Live Graph capability check for a seeded/connected account (no token returned).
     * Useful to see why likes/shares/comments/usernames are missing.
     */
    @PostMapping("/accounts/{accountId}/permission-probe")
    public ResponseEntity<ApiResponse<InstagramPermissionProbeResponse>> permissionProbe(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID accountId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        InstagramAccount account = accountService.requireAccountForUser(accountId, principal);
        String token = account.getAccessToken();

        List<String> missing = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        boolean like = false;
        boolean commentsCount = false;
        boolean shares = false;
        boolean saved = false;
        boolean insightsShares = false;
        boolean insightsSaved = false;
        boolean commentsReadable = false;
        boolean commentUsername = false;

        InstagramGraphClient.MediaPage page =
                graphClient.fetchMediaPage(account.getIgUserId(), token, null);
        if (page.data().isEmpty()) {
            notes.add("No media returned for account");
            missing.add("media_list");
        } else {
            Map<String, Object> first = page.data().getFirst();
            like = first.get("like_count") != null;
            commentsCount = first.get("comments_count") != null;
            shares = first.get("shares_count") != null || first.get("share_count") != null;
            saved = first.get("saved_count") != null || first.get("saves_count") != null;
            if (!like) {
                missing.add("media.like_count");
            }
            if (!commentsCount) {
                missing.add("media.comments_count");
            }
            if (!shares) {
                missing.add("media.shares_count");
            }
            if (!saved) {
                missing.add("media.saved_count");
            }

            String mediaId = first.get("id") == null ? null : first.get("id").toString();
            if (mediaId != null) {
                try {
                    Map<String, Long> insights = graphClient.fetchMediaInsights(mediaId, token);
                    insightsShares = insights.containsKey("shares");
                    insightsSaved = insights.containsKey("saved");
                } catch (InstagramGraphClient.InsightsPermissionException e) {
                    missing.add("insights (need instagram_manage_insights / instagram_business_manage_insights)");
                    notes.add("Insights permission denied by Meta");
                } catch (Exception e) {
                    notes.add("Insights error: " + e.getMessage());
                }
                if (!insightsShares && !shares) {
                    missing.add("shares");
                }
                if (!insightsSaved && !saved) {
                    missing.add("saves");
                }

                List<Map<String, Object>> comments = graphClient.fetchMediaComments(mediaId, token);
                commentsReadable = true;
                if (!comments.isEmpty()) {
                    Map<String, Object> c0 = comments.getFirst();
                    Object uname = c0.get("username");
                    Object from = c0.get("from");
                    commentUsername = (uname != null && !uname.toString().isBlank())
                            || (from instanceof Map<?, ?> fm && fm.get("username") != null);
                    if (!commentUsername) {
                        missing.add("comment.username (need instagram_manage_comments + pages_read_engagement)");
                        notes.add("Comments readable but username omitted by Meta without manage_comments");
                    }
                } else {
                    notes.add("No comments on sample media (or comments edge empty)");
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(new InstagramPermissionProbeResponse(
                account.getId(),
                account.getUsername(),
                account.getScopes(),
                like,
                commentsCount,
                shares,
                saved,
                insightsShares,
                insightsSaved,
                commentsReadable,
                commentUsername,
                List.copyOf(missing),
                List.copyOf(notes)
        )));
    }
}
