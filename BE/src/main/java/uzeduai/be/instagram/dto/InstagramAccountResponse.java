package uzeduai.be.instagram.dto;

import uzeduai.be.instagram.domain.InstagramAccountStatus;

import java.time.Instant;
import java.util.UUID;

public record InstagramAccountResponse(
        UUID id,
        UUID tenantId,
        UUID workspaceId,
        String igUserId,
        String username,
        String accountType,
        InstagramAccountStatus status,
        Instant tokenExpiresAt,
        Instant lastSyncedAt,
        Instant createdAt
) {
}
