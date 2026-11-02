package uzeduai.be.workspace.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.workspace.dto.CreateWorkspaceRequest;
import uzeduai.be.workspace.dto.WorkspaceResponse;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listForUserTenant(UUID userId, UUID tenantId) {
        requireMembership(tenantId, userId);
        return workspaceRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(ws -> new WorkspaceResponse(ws.getId(), tenantId, ws.getName()))
                .toList();
    }

    @Transactional
    public WorkspaceResponse create(UUID userId, CreateWorkspaceRequest request) {
        var membership = requireMembership(request.tenantId(), userId);
        if (membership.getRole() == TenantRole.MEMBER) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_CREATE_FORBIDDEN);
        }
        var tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.WORKSPACE_TENANT_NOT_FOUND));

        Workspace workspace = new Workspace();
        workspace.setTenant(tenant);
        workspace.setName(request.name().trim());
        workspaceRepository.save(workspace);
        return new WorkspaceResponse(workspace.getId(), tenant.getId(), workspace.getName());
    }

    private uzeduai.be.tenant.entity.TenantMember requireMembership(UUID tenantId, UUID userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER));
    }
}
