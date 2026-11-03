package uzeduai.be.instagram.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramMedia;

import java.util.Optional;
import java.util.UUID;

public interface InstagramMediaRepository extends JpaRepository<InstagramMedia, UUID> {

    Optional<InstagramMedia> findByTenantIdAndIgMediaId(UUID tenantId, String igMediaId);

    Optional<InstagramMedia> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<InstagramMedia> findByTenantIdAndWorkspaceIdOrderByPublishedAtDesc(
            UUID tenantId,
            UUID workspaceId,
            Pageable pageable
    );

    Page<InstagramMedia> findByTenantIdAndInstagramAccountIdOrderByPublishedAtDesc(
            UUID tenantId,
            UUID accountId,
            Pageable pageable
    );

    boolean existsByTenantIdAndIgMediaId(UUID tenantId, String igMediaId);
}
