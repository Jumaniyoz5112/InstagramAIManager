package uzeduai.be.tenant.dto;

import uzeduai.be.common.enums.TenantRole;

import java.util.UUID;

public record TenantSummaryResponse(
        UUID id,
        String name,
        String slug,
        TenantRole role
) {
}
