package uzeduai.be.platform.dto;

import uzeduai.be.common.enums.TenantRole;

import java.util.UUID;

/**
 * Safe member projection for platform admin — no password hash, tokens, or secrets.
 */
public record PlatformTenantMember(
        UUID userId,
        String name,
        String email,
        TenantRole role
) {
}
