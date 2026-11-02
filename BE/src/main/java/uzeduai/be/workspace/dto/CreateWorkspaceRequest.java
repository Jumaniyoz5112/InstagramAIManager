package uzeduai.be.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkspaceRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 255) String name
) {
}
