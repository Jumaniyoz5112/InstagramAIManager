package uzeduai.be.workspace.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.workspace.dto.CreateWorkspaceRequest;
import uzeduai.be.workspace.dto.WorkspaceResponse;
import uzeduai.be.workspace.service.WorkspaceService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final AccessService accessService;

    public WorkspaceController(WorkspaceService workspaceService, AccessService accessService) {
        this.workspaceService = workspaceService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID tenantId
    ) {
        accessService.requirePage(principal, "WORKSPACES");
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.listForUserTenant(principal.getId(), tenantId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        accessService.requirePage(principal, "WORKSPACES");
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.create(principal.getId(), request)));
    }
}
