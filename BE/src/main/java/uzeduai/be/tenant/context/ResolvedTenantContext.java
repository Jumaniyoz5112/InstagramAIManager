package uzeduai.be.tenant.context;

import uzeduai.be.common.enums.TenantRole;

import java.util.UUID;

/**
 * Server-verified tenant/workspace snapshot. Empty when membership is missing.
 */
public record ResolvedTenantContext(UUID tenantId, UUID workspaceId, TenantRole tenantRole) {

    public static ResolvedTenantContext empty() {
        return new ResolvedTenantContext(null, null, null);
    }

    public boolean hasTenant() {
        return tenantId != null;
    }
}
