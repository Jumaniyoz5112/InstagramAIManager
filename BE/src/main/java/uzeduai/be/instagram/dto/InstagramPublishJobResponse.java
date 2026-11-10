package uzeduai.be.instagram.dto;

import uzeduai.be.instagram.domain.PublishJobStatus;
import uzeduai.be.instagram.domain.PublishType;

import java.time.Instant;
import java.util.UUID;

public record InstagramPublishJobResponse(
        UUID id,
        UUID accountId,
        PublishType publishType,
        PublishJobStatus status,
        String caption,
        String containerId,
        String publishedIgMediaId,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
