package uzeduai.be.platform.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.common.response.PageResponse;
import uzeduai.be.platform.dto.PlatformStatsResponse;
import uzeduai.be.platform.dto.PlatformTenantDetails;
import uzeduai.be.platform.dto.PlatformTenantMember;
import uzeduai.be.platform.dto.PlatformTenantSummary;
import uzeduai.be.platform.dto.PlatformWorkspaceSummary;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-scoped read APIs. Authorization is role-based ({@code PLATFORM_ADMIN}) only —
 * this service must not use TenantMembershipService or TenantContextHolder.
 */
@Service
public class PlatformAdminService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "name", "slug");

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    public PlatformAdminService(
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
    public PageResponse<PlatformTenantSummary> listTenants(String q, Integer page, Integer size, String sort) {
        Pageable pageable = toPageable(page, size, sort);
        String query = q == null ? "" : q.trim();
        Page<Tenant> tenants = tenantRepository.search(query.isEmpty() ? null : query, pageable);
        List<PlatformTenantSummary> content = tenants.getContent().stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(content, tenants.getNumber(), tenants.getSize(), tenants.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlatformTenantDetails getTenant(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        return toDetails(tenant);
    }

    @Transactional(readOnly = true)
    public List<PlatformWorkspaceSummary> listWorkspaces(UUID tenantId) {
        requireTenant(tenantId);
        return workspaceRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(ws -> new PlatformWorkspaceSummary(
                        ws.getId(),
                        tenantId,
                        ws.getName(),
                        ws.getCreatedAt(),
                        ws.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantMember> listMembers(UUID tenantId) {
        requireTenant(tenantId);
        return tenantMemberRepository.findByTenantIdWithUser(tenantId).stream()
                .map(tm -> new PlatformTenantMember(
                        tm.getUser().getId(),
                        tm.getUser().getFullName(),
                        tm.getUser().getEmail(),
                        tm.getRole()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformStatsResponse stats() {
        return new PlatformStatsResponse(
                tenantRepository.count(),
                userRepository.count(),
                workspaceRepository.count(),
                tenantMemberRepository.count()
        );
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.PLATFORM_TENANT_NOT_FOUND));
    }

    private PlatformTenantSummary toSummary(Tenant tenant) {
        return new PlatformTenantSummary(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                tenantMemberRepository.countByTenantId(tenant.getId()),
                workspaceRepository.countByTenantId(tenant.getId())
        );
    }

    private PlatformTenantDetails toDetails(Tenant tenant) {
        return new PlatformTenantDetails(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                tenantMemberRepository.countByTenantId(tenant.getId()),
                workspaceRepository.countByTenantId(tenant.getId())
        );
    }

    private Pageable toPageable(Integer page, Integer size, String sort) {
        int pageIndex = page == null ? DEFAULT_PAGE : page;
        if (pageIndex < 0) {
            pageIndex = DEFAULT_PAGE;
        }

        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < MIN_SIZE || pageSize > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.PLATFORM_INVALID_PAGE_SIZE);
        }

        Sort.Direction direction = Sort.Direction.DESC;
        String property = DEFAULT_SORT;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            property = parts[0].trim();
            if (!ALLOWED_SORT_FIELDS.contains(property)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.PLATFORM_INVALID_SORT);
            }
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
                direction = Sort.Direction.ASC;
            }
        }

        return PageRequest.of(pageIndex, pageSize, Sort.by(direction, property));
    }
}
