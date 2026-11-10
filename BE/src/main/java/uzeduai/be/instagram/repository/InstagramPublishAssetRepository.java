package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramPublishAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramPublishAssetRepository extends JpaRepository<InstagramPublishAsset, UUID> {

    Optional<InstagramPublishAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InstagramPublishAsset> findByPublishJobIdOrderByPositionAsc(UUID publishJobId);
}
