package uzeduai.be.analysis.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.analysis.dto.SpeakRequest;
import uzeduai.be.analysis.service.SpeechService;
import uzeduai.be.security.user.CustomUserDetails;

@RestController
@RequestMapping("/api/v1/instagram/speech")
public class SpeechController {

    private final SpeechService speechService;
    private final AccessService accessService;

    public SpeechController(SpeechService speechService, AccessService accessService) {
        this.speechService = speechService;
        this.accessService = accessService;
    }

    @PostMapping
    public ResponseEntity<byte[]> speak(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody SpeakRequest request
    ) {
        accessService.requirePage(principal, "INSTAGRAM");
        byte[] wav = speechService.speak(request.text(), request.voice());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .contentLength(wav.length)
                .body(wav);
    }
}
