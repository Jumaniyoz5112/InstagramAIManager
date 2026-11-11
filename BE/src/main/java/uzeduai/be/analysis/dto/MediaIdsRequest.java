package uzeduai.be.analysis.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MediaIdsRequest(
        @NotEmpty @Size(max = 50) List<UUID> mediaIds
) {
}
