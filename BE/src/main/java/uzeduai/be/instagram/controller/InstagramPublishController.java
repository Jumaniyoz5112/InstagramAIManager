package uzeduai.be.instagram.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.domain.PublishType;
import uzeduai.be.instagram.dto.InstagramPublishJobResponse;
import uzeduai.be.instagram.service.InstagramPublishService;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instagram/publish")
public class InstagramPublishController {

    private final InstagramPublishService publishService;
    private final AccessService accessService;

    public InstagramPublishController(InstagramPublishService publishService, AccessService accessService) {
        this.publishService = publishService;
        this.accessService = accessService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<InstagramPublishJobResponse>> publish(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId,
            @RequestParam PublishType publishType,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String idempotencyKey,
            @RequestParam("files") List<MultipartFile> files
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(
                publishService.publish(accountId, publishType, caption, idempotencyKey, files, principal)
        ));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<InstagramPublishJobResponse>> get(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID jobId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(publishService.get(jobId, principal)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InstagramPublishJobResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam UUID accountId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(publishService.list(accountId, principal)));
    }
}
