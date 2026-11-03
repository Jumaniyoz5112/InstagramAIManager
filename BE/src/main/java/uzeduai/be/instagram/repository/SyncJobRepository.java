package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.domain.SyncJobStatus;
import uzeduai.be.instagram.domain.SyncJobType;
import uzeduai.be.instagram.entity.SyncJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    Optional<SyncJob> findFirstByInstagramAccountIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
            UUID accountId,
            SyncJobType jobType,
            List<SyncJobStatus> statuses
    );

    List<SyncJob> findByInstagramAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<SyncJob> findByStatusOrderByCreatedAtAsc(SyncJobStatus status);
}
