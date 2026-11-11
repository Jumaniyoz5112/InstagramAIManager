package uzeduai.be.instagram.dto;

import java.util.List;

public record OauthPickSessionResponse(
        String pickId,
        List<OauthPickCandidateResponse> candidates
) {
}
