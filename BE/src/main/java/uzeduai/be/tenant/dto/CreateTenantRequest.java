package uzeduai.be.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String slug
) {
}
