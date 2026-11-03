package uzeduai.be.instagram.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.entity.InstagramAccount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramAccountRepository extends JpaRepository<InstagramAccount, UUID> {

    List<InstagramAccount> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId);

    Optional<InstagramAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InstagramAccount> findByTenantIdAndIgUserId(UUID tenantId, String igUserId);

    List<InstagramAccount> findByStatusAndTokenExpiresAtBefore(InstagramAccountStatus status, Instant before);

    List<InstagramAccount> findByStatus(InstagramAccountStatus status);
}
