package uzeduai.be.instagram.dto;

import uzeduai.be.instagram.domain.SyncJobStatus;
import uzeduai.be.instagram.domain.SyncJobType;

import java.time.Instant;
import java.util.UUID;

public record SyncJobResponse(
        UUID id,
        UUID accountId,
        SyncJobType jobType,
        SyncJobStatus status,
        int pagesFetched,
        int itemsUpserted,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
