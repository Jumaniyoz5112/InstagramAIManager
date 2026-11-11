package uzeduai.be.instagram.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthPickConfirmRequest(
        @NotBlank String igUserId
) {
}