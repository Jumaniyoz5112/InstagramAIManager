package uzeduai.be.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.auth.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Commits revocations independently so a subsequent API error cannot roll them back.
 */
@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeTokenHash(String tokenHash) {
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }
}
