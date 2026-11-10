package uzeduai.be.instagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.dto.InstagramOauthStartResponse;
import uzeduai.be.instagram.service.InstagramOauthService;
import uzeduai.be.security.user.CustomUserDetails;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/instagram/oauth")
public class InstagramOauthController {

    private final InstagramOauthService oauthService;
    private final InstagramProperties properties;

    public InstagramOauthController(InstagramOauthService oauthService, InstagramProperties properties) {
        this.oauthService = oauthService;
        this.properties = properties;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<InstagramOauthStartResponse>> start(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(oauthService.start(principal)));
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription
    ) {
        if (error != null && !error.isBlank()) {
            return redirectError(errorDescription != null && !errorDescription.isBlank()
                    ? errorDescription
                    : error);
        }
        try {
            var account = oauthService.handleCallback(code, state);
            String base = properties.getFrontendSuccessUrl();
            String sep = base.contains("?") ? "&" : "?";
            return new RedirectView(base + sep + "connected=" + account.getId());
        } catch (ApiException ex) {
            return redirectError(ex.getMessageKey());
        } catch (Exception ex) {
            return redirectError("error.server.internal");
        }
    }

    private RedirectView redirectError(String messageKeyOrText) {
        String base = properties.getFrontendSuccessUrl();
        String sep = base.contains("?") ? "&" : "?";
        String encoded = URLEncoder.encode(messageKeyOrText, StandardCharsets.UTF_8);
        return new RedirectView(base + sep + "ig_error=" + encoded);
    }
}
