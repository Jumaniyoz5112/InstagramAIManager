package uzeduai.be.tenant.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.tenant.dto.CreateTenantRequest;
import uzeduai.be.tenant.dto.TenantSummaryResponse;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    public TenantService(
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> listForUser(UUID userId) {
        return tenantMemberRepository.findByUserIdWithTenant(userId).stream()
                .map(tm -> new TenantSummaryResponse(
                        tm.getTenant().getId(),
                        tm.getTenant().getName(),
                        tm.getTenant().getSlug(),
                        tm.getRole()
                ))
                .toList();
    }

    @Transactional
    public TenantSummaryResponse create(UUID userId, CreateTenantRequest request) {
        String slug = request.slug().trim().toLowerCase();
        if (tenantRepository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu slug band");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Foydalanuvchi topilmadi"));

        Tenant tenant = new Tenant();
        tenant.setName(request.name().trim());
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

        return new TenantSummaryResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), TenantRole.OWNER);
    }
}
