package uzeduai.be.user.dto;

import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.tenant.dto.TenantSummaryResponse;
import uzeduai.be.workspace.dto.WorkspaceResponse;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        List<String> platformRoles,
        UUID activeTenantId,
        UUID activeWorkspaceId,
        TenantRole activeTenantRole,
        List<TenantSummaryResponse> tenants,
        List<WorkspaceResponse> workspaces
) {
}
