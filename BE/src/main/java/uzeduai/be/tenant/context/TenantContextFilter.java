package uzeduai.be.tenant.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;

import java.io.IOException;

/**
 * Re-validates JWT tenant/workspace claims against DB membership and binds
 * {@link TenantContextHolder} for the request. Clears the holder in {@code finally}.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantMembershipService tenantMembershipService;
    private final UserRepository userRepository;

    public TenantContextFilter(
            TenantMembershipService tenantMembershipService,
            UserRepository userRepository
    ) {
        this.tenantMembershipService = tenantMembershipService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof CustomUserDetails principal) {

                ResolvedTenantContext resolved = tenantMembershipService.resolve(
                        principal.getId(),
                        principal.getTenantId(),
                        principal.getWorkspaceId()
                );

                User user = userRepository.findById(principal.getId()).orElse(null);
                if (user != null && user.isEnabled()) {
                    CustomUserDetails rebuilt = new CustomUserDetails(
                            user,
                            resolved.tenantId(),
                            resolved.workspaceId(),
                            resolved.tenantRole()
                    );
                    UsernamePasswordAuthenticationToken updated = new UsernamePasswordAuthenticationToken(
                            rebuilt,
                            authentication.getCredentials(),
                            rebuilt.getAuthorities()
                    );
                    updated.setDetails(authentication.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(updated);
                    TenantContextHolder.set(TenantContext.of(rebuilt.getId(), resolved));
                } else {
                    SecurityContextHolder.clearContext();
                    TenantContextHolder.clear();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
