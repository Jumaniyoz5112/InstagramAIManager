package uzeduai.be.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.user.repository.UserRepository;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            UUID userId = jwtService.userId(claims);
            var user = userRepository.findById(userId).orElse(null);
            if (user != null && user.isEnabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
                UUID tenantId = claimUuid(claims, "tenantId");
                UUID workspaceId = claimUuid(claims, "workspaceId");
                TenantRole tenantRole = null;
                Object roleClaim = claims.get("tenantRole");
                if (roleClaim != null) {
                    tenantRole = TenantRole.valueOf(roleClaim.toString());
                }

                CustomUserDetails principal = new CustomUserDetails(user, tenantId, workspaceId, tenantRole);

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private UUID claimUuid(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }
}
