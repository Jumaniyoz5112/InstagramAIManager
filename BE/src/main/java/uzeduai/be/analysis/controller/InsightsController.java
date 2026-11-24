package uzeduai.be.analysis.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.analysis.dto.GenerateScenarioRequest;
import uzeduai.be.analysis.dto.InsightReportResponse;
import uzeduai.be.analysis.dto.LeaderboardItemResponse;
import uzeduai.be.analysis.dto.ScenarioResponse;
import uzeduai.be.analysis.dto.ScenarioVideoResponse;
import uzeduai.be.analysis.service.ContentInsightsService;
import uzeduai.be.analysis.service.ScenarioVideoService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instagram/insights")
public class InsightsController {

    private final ContentInsightsService insightsService;
    private final ScenarioVideoService videoService;
    private final AccessService accessService;

    public InsightsController(
            ContentInsightsService insightsService,
            ScenarioVideoService videoService,
            AccessService accessService
    ) {
        this.insightsService = insightsService;
        this.videoService = videoService;
        this.accessService = accessService;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardItemResponse>>> leaderboard(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(insightsService.leaderboard(accountId, limit, principal)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<InsightReportResponse>> generate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(insightsService.generateInsights(accountId, principal)));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<InsightReportResponse>> latest(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(insightsService.latestInsights(accountId, principal)));
    }

    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<List<ScenarioResponse>>> listScenarios(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(insightsService.listScenarios(accountId, principal)));
    }

    @PostMapping("/{reportId}/scenarios")
    public ResponseEntity<ApiResponse<ScenarioResponse>> generateScenario(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody(required = false) GenerateScenarioRequest request
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(
                insightsService.generateScenario(
                        reportId,
                        request != null ? request : new GenerateScenarioRequest(null, null),
                        principal
                )
        ));
    }

    @PostMapping("/scenarios/{scenarioId}/video")
    public ResponseEntity<ApiResponse<ScenarioVideoResponse>> renderVideo(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID scenarioId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(videoService.render(scenarioId, principal)));
    }

    @GetMapping("/scenarios/{scenarioId}/video")
    public ResponseEntity<ApiResponse<ScenarioVideoResponse>> latestVideo(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID scenarioId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(videoService.latest(scenarioId, principal)));
    }

    @GetMapping("/scenarios/{scenarioId}/video/file")
    public ResponseEntity<InputStreamResource> videoFile(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID scenarioId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        long size = videoService.fileSize(scenarioId, principal);
        InputStreamResource body = videoService.openFile(scenarioId, principal);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .contentType(MediaType.parseMediaType("video/mp4"));
        if (size >= 0) {
            builder.contentLength(size);
        }
        return builder.body(body);
    }

    @GetMapping("/{reportId}/scenarios/latest")
    public ResponseEntity<ApiResponse<ScenarioResponse>> latestScenario(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID reportId
    ) {
        accessService.requirePage(principal, "IG_INSIGHTS");
        return ResponseEntity.ok(ApiResponse.ok(insightsService.latestScenario(reportId, principal)));
    }
}
