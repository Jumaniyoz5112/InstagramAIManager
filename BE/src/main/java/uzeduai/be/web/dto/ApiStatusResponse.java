package uzeduai.be.web.dto;

import java.time.Instant;
import java.util.Map;

public record ApiStatusResponse(
        String status,
        String service,
        Instant timestamp,
        Map<String, String> details
) {
}
