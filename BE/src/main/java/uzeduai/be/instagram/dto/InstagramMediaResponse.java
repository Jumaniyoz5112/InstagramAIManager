package uzeduai.be.instagram.dto;

import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstagramMediaResponse(
        UUID id,
        UUID accountId,
        String igMediaId,
        InstagramMediaType mediaType,
        String caption,
        String mediaUrl,
        String thumbnailUrl,
        String permalink,
        Instant publishedAt,
        MediaAnalysisStatus analysisStatus,
        List<InstagramMediaChildResponse> children
) {
}
