package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.domain.AnalysisJobStatus;
import uzeduai.be.analysis.domain.AnalysisKind;
import uzeduai.be.analysis.entity.ContentAnalysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentAnalysisRepository extends JpaRepository<ContentAnalysis, UUID> {

    Optional<ContentAnalysis> findByInstagramMediaIdAndAnalysisKind(UUID mediaId, AnalysisKind kind);

    List<ContentAnalysis> findByStatusOrderByCreatedAtAsc(AnalysisJobStatus status);

    Optional<ContentAnalysis> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ContentAnalysis> findByInstagramMediaIdAndTenantIdAndAnalysisKind(
            UUID mediaId,
            UUID tenantId,
            AnalysisKind kind
    );

    List<ContentAnalysis> findByInstagramMediaIdInAndAnalysisKind(List<UUID> mediaIds, AnalysisKind kind);
}
