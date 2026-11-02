package uzeduai.be.tenant.context;

import uzeduai.be.common.enums.TenantRole;

import java.util.UUID;

/**
 * Request-scoped tenant context built only from server-verified membership.
 */
public record TenantContext(
        UUID userId,
        UUID tenantId,
        UUID workspaceId,
        TenantRole tenantRole
) {
    public static TenantContext of(UUID userId, ResolvedTenantContext resolved) {
        return new TenantContext(
                userId,
                resolved.tenantId(),
                resolved.workspaceId(),
                resolved.tenantRole()
        );
    }
}
