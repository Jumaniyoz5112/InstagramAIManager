package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.domain.InsightJobStatus;
import uzeduai.be.analysis.entity.ContentScenarioVideo;

import java.util.Optional;
import java.util.UUID;

public interface ContentScenarioVideoRepository extends JpaRepository<ContentScenarioVideo, UUID> {

    Optional<ContentScenarioVideo> findFirstByScenarioIdAndTenantIdAndStatusOrderByCreatedAtDesc(
            UUID scenarioId,
            UUID tenantId,
            InsightJobStatus status
    );
}
