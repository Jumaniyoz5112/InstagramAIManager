package uzeduai.be.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.auth.dto.AuthTokensResponse;
import uzeduai.be.auth.dto.LoginRequest;
import uzeduai.be.auth.entity.RefreshToken;
import uzeduai.be.auth.repository.RefreshTokenRepository;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.security.jwt.JwtProperties;
import uzeduai.be.security.jwt.JwtService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.dto.TenantSummaryResponse;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.user.dto.UserResponse;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.dto.WorkspaceResponse;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshCookieService refreshCookieService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            TenantMemberRepository tenantMemberRepository,
            WorkspaceRepository workspaceRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshCookieService refreshCookieService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshCookieService = refreshCookieService;
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
        );
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email yoki parol noto‘g‘ri"));
        return issueSession(user, null, null, null, httpRequest, httpResponse);
    }

    @Transactional
    public AuthTokensResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String raw = readRefreshCookie(httpRequest);
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token topilmadi");
        }
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token yaroqsiz"));
        if (!existing.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token bekor qilingan yoki muddati tugagan");
        }

        User user = existing.getUser();
        existing.setRevokedAt(Instant.now());

        // Preserve active context from current access token if present; otherwise null
        return issueSession(user, null, null, null, httpRequest, httpResponse, existing);
    }

    @Transactional
    public AuthTokensResponse activateWorkspace(
            UUID workspaceId,
            CustomUserDetails principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Foydalanuvchi topilmadi"));
        var workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Workspace topilmadi"));
        var membership = tenantMemberRepository
                .findByTenantIdAndUserId(workspace.getTenant().getId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bu workspace uchun ruxsat yo‘q"));

        return issueSession(
                user,
                workspace.getTenant().getId(),
                workspace.getId(),
                membership.getRole(),
                httpRequest,
                httpResponse
        );
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String raw = readRefreshCookie(httpRequest);
        if (raw != null && !raw.isBlank()) {
            refreshTokenRepository.findByTokenHash(hash(raw)).ifPresent(token -> {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            });
        }
        refreshCookieService.clear(httpResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CustomUserDetails principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Foydalanuvchi topilmadi"));
        return toUserResponse(user, principal.getTenantId(), principal.getWorkspaceId(), principal.getTenantRole());
    }

    private AuthTokensResponse issueSession(
            User user,
            UUID tenantId,
            UUID workspaceId,
            TenantRole tenantRole,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return issueSession(user, tenantId, workspaceId, tenantRole, httpRequest, httpResponse, null);
    }

    private AuthTokensResponse issueSession(
            User user,
            UUID tenantId,
            UUID workspaceId,
            TenantRole tenantRole,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            RefreshToken previous
    ) {
        List<String> platformRoles = user.getRoles().stream().map(r -> r.getCode()).toList();
        String accessToken = jwtService.createAccessToken(
                user.getId(), user.getEmail(), platformRoles, tenantId, workspaceId, tenantRole);

        String rawRefresh = generateRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlDays() * 24 * 3600));
        refreshToken.setIpAddress(httpRequest.getRemoteAddr());
        String ua = httpRequest.getHeader("User-Agent");
        if (ua != null && ua.length() > 512) {
            ua = ua.substring(0, 512);
        }
        refreshToken.setUserAgent(ua);
        refreshTokenRepository.save(refreshToken);

        if (previous != null) {
            previous.setReplacedByTokenId(refreshToken.getId());
            refreshTokenRepository.save(previous);
        }

        refreshCookieService.write(httpResponse, rawRefresh);

        UserResponse userResponse = toUserResponse(user, tenantId, workspaceId, tenantRole);
        return new AuthTokensResponse(
                accessToken,
                "Bearer",
                jwtProperties.getAccessTokenTtlMinutes() * 60,
                userResponse
        );
    }

    private UserResponse toUserResponse(User user, UUID tenantId, UUID workspaceId, TenantRole tenantRole) {
        List<String> platformRoles = user.getRoles().stream().map(r -> r.getCode()).toList();
        List<TenantSummaryResponse> tenants = tenantMemberRepository.findByUserIdWithTenant(user.getId()).stream()
                .map(tm -> new TenantSummaryResponse(
                        tm.getTenant().getId(),
                        tm.getTenant().getName(),
                        tm.getTenant().getSlug(),
                        tm.getRole()
                ))
                .toList();

        List<WorkspaceResponse> workspaces = new ArrayList<>();
        for (TenantSummaryResponse tenant : tenants) {
            workspaceRepository.findByTenantIdOrderByCreatedAtAsc(tenant.id()).forEach(ws ->
                    workspaces.add(new WorkspaceResponse(ws.getId(), tenant.id(), ws.getName()))
            );
        }

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                platformRoles,
                tenantId,
                workspaceId,
                tenantRole,
                tenants,
                workspaces
        );
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (jwtProperties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
