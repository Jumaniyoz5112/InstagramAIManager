package uzeduai.be.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.common.response.PageResponse;
import uzeduai.be.platform.dto.PlatformStatsResponse;
import uzeduai.be.platform.dto.PlatformTenantDetails;
import uzeduai.be.platform.dto.PlatformTenantMember;
import uzeduai.be.platform.dto.PlatformTenantSummary;
import uzeduai.be.platform.dto.PlatformWorkspaceSummary;
import uzeduai.be.platform.service.PlatformAdminService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<PageResponse<PlatformTenantSummary>>> listTenants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(ApiResponse.ok(platformAdminService.listTenants(q, page, size, sort)));
    }

    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<ApiResponse<PlatformTenantDetails>> getTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(platformAdminService.getTenant(tenantId)));
    }

    @GetMapping("/tenants/{tenantId}/workspaces")
    public ResponseEntity<ApiResponse<List<PlatformWorkspaceSummary>>> listWorkspaces(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(platformAdminService.listWorkspaces(tenantId)));
    }

    @GetMapping("/tenants/{tenantId}/members")
    public ResponseEntity<ApiResponse<List<PlatformTenantMember>>> listMembers(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(platformAdminService.listMembers(tenantId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(platformAdminService.stats()));
    }
}
