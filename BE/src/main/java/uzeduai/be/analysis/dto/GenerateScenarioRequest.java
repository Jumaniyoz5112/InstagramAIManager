package uzeduai.be.analysis.dto;

import jakarta.validation.constraints.Size;

public record GenerateScenarioRequest(
        @Size(max = 1000) String brief,
        @Size(max = 32) String preferredFormat
) {
}
