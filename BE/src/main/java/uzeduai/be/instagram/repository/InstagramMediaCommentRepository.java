package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramMediaComment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramMediaCommentRepository extends JpaRepository<InstagramMediaComment, UUID> {

    Optional<InstagramMediaComment> findByTenantIdAndIgCommentId(UUID tenantId, String igCommentId);

    List<InstagramMediaComment> findByTenantIdAndInstagramMediaIdOrderByPublishedAtAsc(
            UUID tenantId,
            UUID instagramMediaId
    );
}
