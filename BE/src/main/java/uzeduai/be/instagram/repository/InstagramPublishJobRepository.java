package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramPublishJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramPublishJobRepository extends JpaRepository<InstagramPublishJob, UUID> {

    Optional<InstagramPublishJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InstagramPublishJob> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<InstagramPublishJob> findByTenantIdAndWorkspaceIdAndInstagramAccountIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID workspaceId,
            UUID instagramAccountId
    );
}
