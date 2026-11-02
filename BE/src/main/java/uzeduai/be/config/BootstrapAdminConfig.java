package uzeduai.be.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.enums.PlatformRoleCode;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.user.entity.Role;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.RoleRepository;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapAdminConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:admin@local.dev}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:0110}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-name:Platform Admin}")
    private String adminName;

    @Value("${app.bootstrap.sync-password:false}")
    private boolean syncPassword;

    public BootstrapAdminConfig(
            UserRepository userRepository,
            RoleRepository roleRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
                    role.setCode(PlatformRoleCode.PLATFORM_ADMIN.name());
                    role.setDescription("Global platform administrator");
                    return roleRepository.save(role);
                });

        User user = userRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> {
            User created = new User();
            created.setEmail(adminEmail.toLowerCase());
            created.setFullName(adminName);
            created.setEnabled(true);
            created.setRoles(new HashSet<>(Set.of(adminRole)));
            created.setPasswordHash(passwordEncoder.encode(adminPassword));
            return userRepository.save(created);
        });

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(new HashSet<>(Set.of(adminRole)));
            userRepository.save(user);
        } else if (user.getRoles().stream().noneMatch(r -> PlatformRoleCode.PLATFORM_ADMIN.name().equals(r.getCode()))) {
            user.getRoles().add(adminRole);
            userRepository.save(user);
        }

        // Dev-only convenience: sync password when explicitly enabled
        if (syncPassword && !passwordEncoder.matches(adminPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            userRepository.save(user);
            log.info("Bootstrap admin paroli yangilandi (sync-password=true): {}", adminEmail);
        }

        if (!tenantRepository.existsBySlug("demo")) {
            Tenant tenant = new Tenant();
            tenant.setName("Demo Tenant");
            tenant.setSlug("demo");
            tenantRepository.save(tenant);

            TenantMember member = new TenantMember();
            member.setTenant(tenant);
            member.setUser(user);
            member.setRole(TenantRole.OWNER);
            tenantMemberRepository.save(member);

            Workspace workspace = new Workspace();
            workspace.setTenant(tenant);
            workspace.setName("Demo Workspace");
            workspaceRepository.save(workspace);
        }

        log.info("Bootstrap admin ready: {}", adminEmail);
    }
}
