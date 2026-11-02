package uzeduai.be.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformTenantSummary(
        UUID id,
        String name,
        String slug,
        Instant createdAt,
        Instant updatedAt,
        long memberCount,
        long workspaceCount
) {
}
