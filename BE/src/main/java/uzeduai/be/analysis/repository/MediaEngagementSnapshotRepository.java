package uzeduai.be.analysis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.analysis.entity.MediaEngagementSnapshot;

import java.util.List;
import java.util.UUID;

public interface MediaEngagementSnapshotRepository extends JpaRepository<MediaEngagementSnapshot, UUID> {

    List<MediaEngagementSnapshot> findByTenantIdAndInstagramMediaIdOrderByCapturedAtDesc(
            UUID tenantId,
            UUID mediaId
    );
}
