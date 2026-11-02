package uzeduai.be.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.auth.entity.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
