package uzeduai.be.instagram.dto;

import java.time.Instant;
import java.util.UUID;

public record InstagramMediaCommentResponse(
        UUID id,
        String igCommentId,
        String parentIgCommentId,
        String username,
        String text,
        Integer likeCount,
        Instant publishedAt
) {
}
