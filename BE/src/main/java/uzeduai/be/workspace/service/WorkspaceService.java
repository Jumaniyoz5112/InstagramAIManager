package uzeduai.be.workspace.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.tenant.context.TenantMembershipService;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.workspace.dto.CreateWorkspaceRequest;
import uzeduai.be.workspace.dto.WorkspaceResponse;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TenantMembershipService tenantMembershipService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            TenantMembershipService tenantMembershipService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.tenantMembershipService = tenantMembershipService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listForUserTenant(UUID userId, UUID tenantId) {
        tenantMembershipService.requireMembership(tenantId, userId);
        return workspaceRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(ws -> new WorkspaceResponse(ws.getId(), tenantId, ws.getName()))
                .toList();
    }

    @Transactional
    public WorkspaceResponse create(UUID userId, CreateWorkspaceRequest request) {
        var membership = tenantMembershipService.requireMembership(request.tenantId(), userId);
        if (membership.getRole() == TenantRole.MEMBER) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_CREATE_FORBIDDEN);
        }
        Tenant tenant = membership.getTenant();
        if (tenant == null || !tenant.getId().equals(request.tenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, MessageKeys.WORKSPACE_TENANT_NOT_FOUND);
        }

        Workspace workspace = new Workspace();
        workspace.setTenant(tenant);
        workspace.setName(request.name().trim());
        workspaceRepository.save(workspace);
        return new WorkspaceResponse(workspace.getId(), tenant.getId(), workspace.getName());
    }
}
