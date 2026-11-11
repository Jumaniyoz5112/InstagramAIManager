package uzeduai.be.analysis.dto;

import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;

import java.util.UUID;

public record LeaderboardItemResponse(
        UUID mediaId,
        String igMediaId,
        InstagramMediaType mediaType,
        String captionPreview,
        String thumbnailUrl,
        Integer likeCount,
        Integer commentsCount,
        Integer sharesCount,
        Integer savesCount,
        double engagementScore,
        int rank,
        String bucket,
        MediaAnalysisStatus analysisStatus,
        String analysisSummary
) {
}
