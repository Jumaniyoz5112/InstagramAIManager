package uzeduai.be.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.access.service.AccessService;
import uzeduai.be.auth.dto.AuthTokensResponse;
import uzeduai.be.auth.dto.LoginRequest;
import uzeduai.be.auth.dto.RegisterRequest;
import uzeduai.be.auth.entity.RefreshToken;
import uzeduai.be.auth.repository.RefreshTokenRepository;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.security.jwt.JwtProperties;
import uzeduai.be.security.jwt.JwtService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.dto.TenantSummaryResponse;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.user.dto.UserResponse;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.dto.WorkspaceResponse;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshCookieService refreshCookieService;
    private final AccessService accessService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            WorkspaceRepository workspaceRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenRevocationService refreshTokenRevocationService,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshCookieService refreshCookieService,
            AccessService accessService
    ) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshCookieService = refreshCookieService;
        this.accessService = accessService;
    }

    @Transactional
    public AuthTokensResponse register(
            RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String email = request.email().trim().toLowerCase();
        String slug = request.tenantSlug().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, MessageKeys.AUTH_EMAIL_TAKEN);
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, MessageKeys.AUTH_TENANT_SLUG_TAKEN);
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(new HashSet<>());
        userRepository.save(user);

        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName().trim());
        tenant.setSlug(slug);
        tenantRepository.save(tenant);

        TenantMember member = new TenantMember();
        member.setTenant(tenant);
        member.setUser(user);
        member.setRole(TenantRole.OWNER);
        tenantMemberRepository.save(member);

        Workspace workspace = new Workspace();
        workspace.setTenant(tenant);
        workspace.setName("Default");
        workspaceRepository.save(workspace);

        accessService.grantDefaultPages(user);

        return issueSession(
                user,
                tenant.getId(),
                workspace.getId(),
                TenantRole.OWNER,
                httpRequest,
                httpResponse
        );
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
        );
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_INVALID_CREDENTIALS));
        return issueSession(user, null, null, null, httpRequest, httpResponse);
    }

    @Transactional
    public AuthTokensResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String raw = readRefreshCookie(httpRequest);
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_MISSING);
        }

        String tokenHash = hash(raw);
        Instant now = Instant.now();

        RefreshToken existing = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_INVALID));

        User user = existing.getUser();

        // Reuse of an already-revoked token → revoke the whole active family (committed)
        if (existing.getRevokedAt() != null) {
            refreshTokenRevocationService.revokeAllActiveForUser(user.getId());
            refreshCookieService.clear(httpResponse);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_REUSE);
        }

        if (!existing.getExpiresAt().isAfter(now)) {
            refreshTokenRevocationService.revokeTokenHash(tokenHash);
            refreshCookieService.clear(httpResponse);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_EXPIRED);
        }

        if (!user.isEnabled()) {
            refreshTokenRevocationService.revokeAllActiveForUser(user.getId());
            refreshCookieService.clear(httpResponse);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_USER_DISABLED);
        }

        // Atomic claim: only one parallel refresh wins; loser gets INVALID (no fork / no false reuse)
        int claimed = refreshTokenRepository.markRevokedIfActive(tokenHash, now);
        if (claimed != 1) {
            refreshCookieService.clear(httpResponse);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_INVALID);
        }

        existing = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_REFRESH_INVALID));
        user = existing.getUser();

        SessionContext context = resolveSessionContext(user, existing.getTenantId(), existing.getWorkspaceId());

        return issueSession(
                user,
                context.tenantId(),
                context.workspaceId(),
                context.tenantRole(),
                httpRequest,
                httpResponse,
                existing
        );
    }

    @Transactional
    public AuthTokensResponse activateWorkspace(
            UUID workspaceId,
            CustomUserDetails principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_USER_NOT_FOUND));
        var workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.AUTH_WORKSPACE_NOT_FOUND));
        var membership = tenantMemberRepository
                .findByTenantIdAndUserId(workspace.getTenant().getId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, MessageKeys.AUTH_WORKSPACE_FORBIDDEN));

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
            refreshTokenRepository.findByTokenHashWithUser(hash(raw)).ifPresent(token ->
                    refreshTokenRevocationService.revokeAllActiveForUser(token.getUser().getId())
            );
        }
        refreshCookieService.clear(httpResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CustomUserDetails principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MessageKeys.AUTH_USER_NOT_FOUND));
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
        refreshToken.setTenantId(tenantId);
        refreshToken.setWorkspaceId(workspaceId);
        refreshToken.setTenantRole(tenantRole);
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

    private SessionContext resolveSessionContext(User user, UUID tenantId, UUID workspaceId) {
        if (tenantId == null) {
            return new SessionContext(null, null, null);
        }

        var membership = tenantMemberRepository.findByTenantIdAndUserId(tenantId, user.getId());
        if (membership.isEmpty()) {
            return new SessionContext(null, null, null);
        }

        TenantRole role = membership.get().getRole();
        UUID resolvedWorkspaceId = null;
        if (workspaceId != null) {
            resolvedWorkspaceId = workspaceRepository.findByIdAndTenantId(workspaceId, tenantId)
                    .map(Workspace::getId)
                    .orElse(null);
        }
        return new SessionContext(tenantId, resolvedWorkspaceId, role);
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
                workspaces,
                accessService.resolvePages(user)
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

    private record SessionContext(UUID tenantId, UUID workspaceId, TenantRole tenantRole) {
    }
}
