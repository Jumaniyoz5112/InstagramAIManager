package uzeduai.be.analysis.dto;

import uzeduai.be.analysis.domain.InsightJobStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InsightReportResponse(
        UUID id,
        UUID accountId,
        InsightJobStatus status,
        String modelName,
        String promptVersion,
        String schemaVersion,
        Map<String, Object> inputSnapshot,
        Map<String, Object> result,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
