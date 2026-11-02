package uzeduai.be.access.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.dto.PageAccessResponse;
import uzeduai.be.access.dto.UpdateUserPagesRequest;
import uzeduai.be.access.dto.UserAccessResponse;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AccessController {

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/pages")
    public ResponseEntity<ApiResponse<List<PageAccessResponse>>> pages() {
        return ResponseEntity.ok(ApiResponse.ok(accessService.listCatalog()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserAccessResponse>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(accessService.listUsers()));
    }

    @PutMapping("/users/{userId}/pages")
    public ResponseEntity<ApiResponse<UserAccessResponse>> updateUserPages(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserPagesRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(accessService.updateUserPages(userId, request, principal)));
    }
}
