package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramMediaChild;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramMediaChildRepository extends JpaRepository<InstagramMediaChild, UUID> {

    List<InstagramMediaChild> findByParentMediaIdOrderByPositionAsc(UUID parentMediaId);

    Optional<InstagramMediaChild> findByParentMediaIdAndIgMediaId(UUID parentMediaId, String igMediaId);
}
