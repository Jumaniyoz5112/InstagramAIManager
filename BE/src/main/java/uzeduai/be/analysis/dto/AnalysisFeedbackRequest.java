package uzeduai.be.analysis.dto;

import jakarta.validation.constraints.Size;

public record AnalysisFeedbackRequest(
        @Size(max = 500) String correctedSummary,
        @Size(max = 500) String userNote
) {
}
