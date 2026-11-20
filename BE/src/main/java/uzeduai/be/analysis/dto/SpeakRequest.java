package uzeduai.be.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeakRequest(
        @NotBlank @Size(max = 500) String text,
        @Size(max = 40) String voice
) {
}
