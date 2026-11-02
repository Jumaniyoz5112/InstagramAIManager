package uzeduai.be.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformWorkspaceSummary(
        UUID id,
        UUID tenantId,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}
