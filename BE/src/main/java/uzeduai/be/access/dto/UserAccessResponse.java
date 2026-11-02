package uzeduai.be.access.dto;

import java.util.List;
import java.util.UUID;

public record UserAccessResponse(
        UUID id,
        String email,
        String fullName,
        boolean platformAdmin,
        boolean enabled,
        List<String> pageCodes
) {
}
