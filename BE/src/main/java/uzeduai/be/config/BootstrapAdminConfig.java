package uzeduai.be.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.util.Set;
import java.util.UUID;

@Component
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

    @Value("${app.bootstrap.admin-password:ChangeMe0110!}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-name:Platform Admin}")
    private String adminName;

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
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }

        Role adminRole = roleRepository.findByCode(PlatformRoleCode.PLATFORM_ADMIN.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
                    role.setCode(PlatformRoleCode.PLATFORM_ADMIN.name());
                    role.setDescription("Global platform administrator");
                    return roleRepository.save(role);
                });

        User user = new User();
        user.setEmail(adminEmail.toLowerCase());
        user.setFullName(adminName);
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setEnabled(true);
        user.setRoles(Set.of(adminRole));
        userRepository.save(user);

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

        log.info("Bootstrap admin yaratildi: {} (parolni env orqali o‘zgartiring)", adminEmail);
    }
}
