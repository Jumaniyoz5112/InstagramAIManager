package uzeduai.be.instagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.common.response.PageResponse;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.dto.InstagramMediaCommentResponse;
import uzeduai.be.instagram.dto.InstagramMediaResponse;
import uzeduai.be.instagram.service.InstagramMediaService;
import uzeduai.be.security.user.CustomUserDetails;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instagram/media")
public class InstagramMediaController {

    private final InstagramMediaService mediaService;
    private final AccessService accessService;

    public InstagramMediaController(InstagramMediaService mediaService, AccessService accessService) {
        this.mediaService = mediaService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InstagramMediaResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) InstagramMediaType mediaType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(mediaService.list(principal, accountId, mediaType, page, size)));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<InstagramMediaResponse>> get(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID mediaId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(mediaService.get(mediaId, principal)));
    }

    @GetMapping("/{mediaId}/comments")
    public ResponseEntity<ApiResponse<List<InstagramMediaCommentResponse>>> comments(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID mediaId
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        return ResponseEntity.ok(ApiResponse.ok(mediaService.listComments(mediaId, principal)));
    }
}
