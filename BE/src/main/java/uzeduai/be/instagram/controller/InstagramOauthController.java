package uzeduai.be.instagram.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.dto.InstagramAccountResponse;
import uzeduai.be.instagram.dto.InstagramOauthStartResponse;
import uzeduai.be.instagram.dto.OauthPickConfirmRequest;
import uzeduai.be.instagram.dto.OauthPickSessionResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.service.InstagramAccountService;
import uzeduai.be.instagram.service.InstagramOauthService;
import uzeduai.be.security.user.CustomUserDetails;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/instagram/oauth")
public class InstagramOauthController {

    private final InstagramOauthService oauthService;
    private final InstagramProperties properties;
    private final InstagramAccountService accountService;

    public InstagramOauthController(
            InstagramOauthService oauthService,
            InstagramProperties properties,
            InstagramAccountService accountService
    ) {
        this.oauthService = oauthService;
        this.properties = properties;
        this.accountService = accountService;
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
            var outcome = oauthService.handleCallback(code, state);
            String base = properties.getFrontendSuccessUrl();
            String sep = base.contains("?") ? "&" : "?";
            if (outcome.needsPick()) {
                return new RedirectView(base + sep + "ig_pick=" + outcome.pickId());
            }
            return new RedirectView(base + sep + "connected=" + outcome.accountId());
        } catch (ApiException ex) {
            return redirectError(ex.getMessageKey());
        } catch (Exception ex) {
            return redirectError("error.server.internal");
        }
    }

    @GetMapping("/pick/{pickId}")
    public ResponseEntity<ApiResponse<OauthPickSessionResponse>> pickSession(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String pickId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(oauthService.getPickSession(pickId, principal)));
    }

    @PostMapping("/pick/{pickId}/confirm")
    public ResponseEntity<ApiResponse<InstagramAccountResponse>> confirmPick(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String pickId,
            @Valid @RequestBody OauthPickConfirmRequest request
    ) {
        InstagramAccount account = oauthService.confirmPick(pickId, request.igUserId(), principal);
        return ResponseEntity.ok(ApiResponse.ok(accountService.toResponse(account)));
    }

    private RedirectView redirectError(String messageKeyOrText) {
        String base = properties.getFrontendSuccessUrl();
        String sep = base.contains("?") ? "&" : "?";
        String encoded = URLEncoder.encode(messageKeyOrText, StandardCharsets.UTF_8);
        return new RedirectView(base + sep + "ig_error=" + encoded);
    }
}
