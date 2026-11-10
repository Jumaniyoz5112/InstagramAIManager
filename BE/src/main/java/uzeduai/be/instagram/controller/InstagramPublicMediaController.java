package uzeduai.be.instagram.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.instagram.entity.InstagramPublishAsset;
import uzeduai.be.instagram.service.InstagramPublishService;
import uzeduai.be.instagram.service.PublishMediaUrlSigner;
import uzeduai.be.storage.StoragePort;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Public (signed) media fetch for Meta Content Publishing crawlers.
 * Auth: HMAC query sig + expiry — not JWT.
 */
@RestController
@RequestMapping("/api/v1/instagram/public-media")
public class InstagramPublicMediaController {

    private final InstagramPublishService publishService;
    private final PublishMediaUrlSigner urlSigner;
    private final StoragePort storagePort;

    public InstagramPublicMediaController(
            InstagramPublishService publishService,
            PublishMediaUrlSigner urlSigner,
            StoragePort storagePort
    ) {
        this.publishService = publishService;
        this.urlSigner = urlSigner;
        this.storagePort = storagePort;
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<InputStreamResource> get(
            @PathVariable UUID assetId,
            @RequestParam long exp,
            @RequestParam String sig
    ) {
        if (!urlSigner.isValid(assetId, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        InstagramPublishAsset asset = publishService.requireAsset(assetId);
        if (!storagePort.exists(asset.getStorageKey())) {
            return ResponseEntity.notFound().build();
        }
        InputStream stream = storagePort.open(asset.getStorageKey());
        MediaType mediaType = resolveMediaType(asset.getContentType(), asset.getOriginalFilename());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(mediaType)
                .body(new InputStreamResource(stream));
    }

    private static MediaType resolveMediaType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                // fall through
            }
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (name.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (name.endsWith(".mov")) {
            return MediaType.parseMediaType("video/quicktime");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
