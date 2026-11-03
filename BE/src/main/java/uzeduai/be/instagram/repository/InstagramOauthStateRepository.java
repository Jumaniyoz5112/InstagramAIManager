package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.entity.InstagramOauthState;

import java.util.Optional;
import java.util.UUID;

public interface InstagramOauthStateRepository extends JpaRepository<InstagramOauthState, UUID> {
    Optional<InstagramOauthState> findByState(String state);
}
