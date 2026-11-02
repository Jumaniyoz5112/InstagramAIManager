package uzeduai.be.tenant.context;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.UUID;

@Service
public class TenantMembershipService {

    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public TenantMembershipService(
            TenantMemberRepository tenantMemberRepository,
            WorkspaceRepository workspaceRepository
    ) {
        this.tenantMemberRepository = tenantMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Re-validates claimed tenant/workspace against current DB membership.
     * Returns empty context when membership is missing (soft-clear policy).
     */
    @Transactional(readOnly = true)
    public ResolvedTenantContext resolve(UUID userId, UUID tenantId, UUID workspaceId) {
        if (userId == null || tenantId == null) {
            return ResolvedTenantContext.empty();
        }

        var membership = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId);
        if (membership.isEmpty()) {
            return ResolvedTenantContext.empty();
        }

        UUID resolvedWorkspaceId = null;
        if (workspaceId != null) {
            resolvedWorkspaceId = workspaceRepository.findByIdAndTenantId(workspaceId, tenantId)
                    .map(Workspace::getId)
                    .orElse(null);
        }

        return new ResolvedTenantContext(tenantId, resolvedWorkspaceId, membership.get().getRole());
    }

    @Transactional(readOnly = true)
    public TenantMember requireMembership(UUID tenantId, UUID userId) {
        return tenantMemberRepository.findByTenantIdAndUserIdWithTenant(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER));
    }

    @Transactional(readOnly = true)
    public Tenant requireMemberTenant(UUID tenantId, UUID userId) {
        return requireMembership(tenantId, userId).getTenant();
    }

    @Transactional(readOnly = true)
    public Workspace requireWorkspaceInTenant(UUID workspaceId, UUID tenantId) {
        return workspaceRepository.findByIdAndTenantId(workspaceId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.AUTH_WORKSPACE_NOT_FOUND));
    }
}
