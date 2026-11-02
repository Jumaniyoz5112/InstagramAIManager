package uzeduai.be.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.auth.entity.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("""
            select rt from RefreshToken rt
            join fetch rt.user u
            left join fetch u.roles
            where rt.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Query("""
            select count(rt) from RefreshToken rt
            where rt.user.id = :userId
              and rt.revokedAt is null
              and rt.expiresAt > :now
            """)
    long countActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.tokenHash = :tokenHash
              and rt.revokedAt is null
              and rt.expiresAt > :now
            """)
    int markRevokedIfActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.user.id = :userId
              and rt.revokedAt is null
            """)
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
