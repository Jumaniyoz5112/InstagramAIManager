package uzeduai.be.access.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserPagesRequest(
        @NotNull List<String> pageCodes
) {
}
