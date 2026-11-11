package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.entity.ContentInsightReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentInsightReportRepository extends JpaRepository<ContentInsightReport, UUID> {

    Optional<ContentInsightReport> findFirstByTenantIdAndWorkspaceIdAndInstagramAccountIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workspaceId,
            UUID accountId
    );

    Optional<ContentInsightReport> findFirstByTenantIdAndWorkspaceIdAndInstagramAccountIdIsNullOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workspaceId
    );

    Optional<ContentInsightReport> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ContentInsightReport> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workspaceId
    );
}
