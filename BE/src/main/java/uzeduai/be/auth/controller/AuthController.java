package uzeduai.be.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uzeduai.be.auth.dto.AuthTokensResponse;
import uzeduai.be.auth.dto.LoginRequest;
import uzeduai.be.auth.service.AuthService;
import uzeduai.be.common.response.ApiResponse;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.user.dto.UserResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokensResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, httpRequest, httpResponse)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokensResponse>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(httpRequest, httpResponse)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.okMessage("Chiqlildi"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(principal)));
    }

    @PostMapping("/workspaces/{workspaceId}/activate")
    public ResponseEntity<ApiResponse<AuthTokensResponse>> activateWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.activateWorkspace(workspaceId, principal, httpRequest, httpResponse)));
    }
}
