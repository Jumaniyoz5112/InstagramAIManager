package uzeduai.be.analysis.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.analysis.domain.InsightJobStatus;
import uzeduai.be.analysis.entity.ContentScenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentScenarioRepository extends JpaRepository<ContentScenario, UUID> {

    Optional<ContentScenario> findFirstByInsightReportIdOrderByCreatedAtDesc(UUID insightReportId);

    Optional<ContentScenario> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ContentScenario> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId);

    @Query("""
            select s from ContentScenario s, ContentInsightReport r
            where s.insightReportId = r.id
              and s.tenantId = :tenantId
              and s.workspaceId = :workspaceId
              and r.tenantId = :tenantId
              and r.instagramAccountId = :accountId
              and s.status = :status
            order by s.createdAt desc
            """)
    List<ContentScenario> findReadyForAccount(
            @Param("tenantId") UUID tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("accountId") UUID accountId,
            @Param("status") InsightJobStatus status,
            Pageable pageable
    );
}
