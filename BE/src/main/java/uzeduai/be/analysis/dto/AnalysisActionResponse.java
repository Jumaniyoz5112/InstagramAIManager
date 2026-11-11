package uzeduai.be.analysis.dto;

import java.util.List;
import java.util.UUID;

public record AnalysisActionResponse(
        int queued,
        int skipped,
        int ignored,
        List<UUID> mediaIds
) {
}
