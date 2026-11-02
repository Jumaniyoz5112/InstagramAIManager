package uzeduai.be.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.web.dto.ApiStatusResponse;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiStatusResponse> health() {
        return ResponseEntity.ok(new ApiStatusResponse(
                "UP",
                "InstagramAIManager backend",
                Instant.now(),
                Map.of(
                        "service", "BE",
                        "database", "InstagramAIManager"
                )
        ));
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("message", "pong"));
    }
}
