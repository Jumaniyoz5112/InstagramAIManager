package uzeduai.be.analysis.dto;

import uzeduai.be.analysis.domain.EngagementSlot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EngagementSnapshotResponse(
        UUID id,
        UUID mediaId,
        EngagementSlot slot,
        Integer likeCount,
        Integer commentsCount,
        Integer sharesCount,
        Integer savesCount,
        List<Map<String, Object>> commentsSample,
        Instant capturedAt
) {
}
