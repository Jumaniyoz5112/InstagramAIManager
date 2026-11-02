package uzeduai.be.auth.dto;

import uzeduai.be.user.dto.UserResponse;

public record AuthTokensResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
}
