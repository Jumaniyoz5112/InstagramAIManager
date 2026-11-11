package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.entity.ContentAnalysisResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentAnalysisResultRepository extends JpaRepository<ContentAnalysisResult, UUID> {

    Optional<ContentAnalysisResult> findByContentAnalysisId(UUID contentAnalysisId);

    List<ContentAnalysisResult> findByContentAnalysisIdIn(List<UUID> contentAnalysisIds);
}
