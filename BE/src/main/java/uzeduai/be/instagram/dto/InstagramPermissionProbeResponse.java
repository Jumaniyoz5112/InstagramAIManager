package uzeduai.be.instagram.dto;

import java.util.List;
import java.util.UUID;

public record InstagramPermissionProbeResponse(
        UUID accountId,
        String username,
        String scopes,
        boolean mediaLikeCount,
        boolean mediaCommentsCount,
        boolean mediaSharesCount,
        boolean mediaSavedCount,
        boolean insightsShares,
        boolean insightsSaved,
        boolean commentsReadable,
        boolean commentUsernameReadable,
        List<String> missingCapabilities,
        List<String> notes
) {
}
