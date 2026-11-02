package uzeduai.be.security.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.user.entity.User;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<GrantedAuthority> authorities;
    private final UUID tenantId;
    private final UUID workspaceId;
    private final TenantRole tenantRole;

    public CustomUserDetails(User user) {
        this(user, null, null, null);
    }

    public CustomUserDetails(User user, UUID tenantId, UUID workspaceId, TenantRole tenantRole) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .collect(Collectors.toCollection(HashSet::new));
        if (tenantRole != null) {
            this.authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_" + tenantRole.name()));
        }
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.tenantRole = tenantRole;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public TenantRole getTenantRole() {
        return tenantRole;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
