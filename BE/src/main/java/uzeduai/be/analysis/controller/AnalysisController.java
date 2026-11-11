package uzeduai.be.analysis.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.analysis.dto.AnalysisActionResponse;
import uzeduai.be.analysis.dto.AnalysisFeedbackRequest;
import uzeduai.be.analysis.dto.ContentAnalysisResponse;
import uzeduai.be.analysis.dto.EngagementSnapshotResponse;
import uzeduai.be.analysis.dto.MediaIdsRequest;
import uzeduai.be.analysis.service.ContentAnalysisService;
import uzeduai.be.analysis.service.EngagementSnapshotService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instagram/media")
public class AnalysisController {

    private final ContentAnalysisService contentAnalysisService;
    private final EngagementSnapshotService engagementSnapshotService;
    private final AccessService accessService;

    public AnalysisController(
            ContentAnalysisService contentAnalysisService,
            EngagementSnapshotService engagementSnapshotService,
            AccessService accessService
    ) {
        this.contentAnalysisService = contentAnalysisService;
        this.engagementSnapshotService = engagementSnapshotService;
        this.accessService = accessService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AnalysisActionResponse>> analyzeSelected(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody MediaIdsRequest request
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(
                contentAnalysisService.enqueueSelected(request.mediaIds(), principal)
        ));
    }

    @PostMapping("/skip-analysis")
    public ResponseEntity<ApiResponse<AnalysisActionResponse>> skipSelected(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody MediaIdsRequest request
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(
                contentAnalysisService.skipSelected(request.mediaIds(), principal)
        ));
    }

    @GetMapping("/{mediaId}/analysis")
    public ResponseEntity<ApiResponse<ContentAnalysisResponse>> analysis(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID mediaId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(contentAnalysisService.getForMedia(mediaId, principal)));
    }

    @PutMapping("/{mediaId}/analysis/feedback")
    public ResponseEntity<ApiResponse<ContentAnalysisResponse>> saveFeedback(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID mediaId,
            @Valid @RequestBody AnalysisFeedbackRequest request
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(
                contentAnalysisService.saveUserFeedback(mediaId, request, principal)
        ));
    }

    @GetMapping("/{mediaId}/engagement-snapshots")
    public ResponseEntity<ApiResponse<List<EngagementSnapshotResponse>>> engagementSnapshots(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID mediaId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(engagementSnapshotService.listForMedia(mediaId, principal)));
    }
}
