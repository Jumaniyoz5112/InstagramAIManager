package uzeduai.be.analysis.dto;

import uzeduai.be.analysis.domain.AnalysisJobStatus;
import uzeduai.be.analysis.domain.AnalysisKind;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ContentAnalysisResponse(
        UUID id,
        UUID mediaId,
        AnalysisKind analysisKind,
        AnalysisJobStatus status,
        String modelName,
        String promptVersion,
        String schemaVersion,
        Map<String, Object> result,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
