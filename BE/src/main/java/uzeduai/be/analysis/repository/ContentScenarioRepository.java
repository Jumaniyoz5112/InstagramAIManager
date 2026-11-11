package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.entity.ContentScenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentScenarioRepository extends JpaRepository<ContentScenario, UUID> {

    Optional<ContentScenario> findFirstByInsightReportIdOrderByCreatedAtDesc(UUID insightReportId);

    Optional<ContentScenario> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ContentScenario> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId);
}
