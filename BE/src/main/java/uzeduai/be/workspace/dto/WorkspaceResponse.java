package uzeduai.be.workspace.dto;

import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        UUID tenantId,
        String name
) {
}
