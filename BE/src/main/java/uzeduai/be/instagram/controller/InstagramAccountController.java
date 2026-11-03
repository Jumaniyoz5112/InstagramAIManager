package uzeduai.be.instagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.dto.InstagramAccountResponse;
import uzeduai.be.instagram.dto.SyncJobResponse;
import uzeduai.be.instagram.service.InstagramAccountService;
import uzeduai.be.instagram.service.InstagramSyncService;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instagram/accounts")
public class InstagramAccountController {

    private final InstagramAccountService accountService;
    private final InstagramSyncService syncService;
    private final AccessService accessService;

    public InstagramAccountController(
            InstagramAccountService accountService,
            InstagramSyncService syncService,
            AccessService accessService
    ) {
        this.accountService = accountService;
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InstagramAccountResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(accountService.listForActiveWorkspace(principal)));
    }

    @PostMapping("/{accountId}/sync")
    public ResponseEntity<ApiResponse<SyncJobResponse>> sync(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID accountId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(syncService.triggerSync(accountId, principal)));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<InstagramAccountResponse>> disconnect(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID accountId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(accountService.disconnect(accountId, principal)));
    }
}
