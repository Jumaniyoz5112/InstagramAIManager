package uzeduai.be.analysis.dto;

import uzeduai.be.analysis.domain.InsightJobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScenarioVideoResponse(
        UUID id,
        UUID scenarioId,
        InsightJobStatus status,
        String contentType,
        Long sizeBytes,
        BigDecimal durationSec,
        String errorMessage,
        Instant createdAt
) {
}
