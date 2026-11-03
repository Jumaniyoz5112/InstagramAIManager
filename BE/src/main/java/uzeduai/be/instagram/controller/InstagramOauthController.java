package uzeduai.be.instagram.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.dto.InstagramOauthStartResponse;
import uzeduai.be.instagram.service.InstagramOauthService;
import uzeduai.be.security.user.CustomUserDetails;

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
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        var account = oauthService.handleCallback(code, state);
        String base = properties.getFrontendSuccessUrl();
        String sep = base.contains("?") ? "&" : "?";
        return new RedirectView(base + sep + "connected=" + account.getId());
    }
}
