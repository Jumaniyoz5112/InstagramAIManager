package uzeduai.be.instagram.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;
import uzeduai.be.instagram.entity.InstagramMedia;

import java.util.List;
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

    Page<InstagramMedia> findByTenantIdAndWorkspaceIdAndMediaTypeOrderByPublishedAtDesc(
            UUID tenantId,
            UUID workspaceId,
            InstagramMediaType mediaType,
            Pageable pageable
    );

    Page<InstagramMedia> findByTenantIdAndInstagramAccountIdOrderByPublishedAtDesc(
            UUID tenantId,
            UUID accountId,
            Pageable pageable
    );

    List<InstagramMedia> findByTenantIdAndInstagramAccountId(UUID tenantId, UUID accountId);

    Page<InstagramMedia> findByTenantIdAndInstagramAccountIdAndMediaTypeOrderByPublishedAtDesc(
            UUID tenantId,
            UUID accountId,
            InstagramMediaType mediaType,
            Pageable pageable
    );

    boolean existsByTenantIdAndIgMediaId(UUID tenantId, String igMediaId);

    List<InstagramMedia> findByAnalysisStatusOrderByPublishedAtDesc(
            MediaAnalysisStatus analysisStatus,
            Pageable pageable
    );
}
