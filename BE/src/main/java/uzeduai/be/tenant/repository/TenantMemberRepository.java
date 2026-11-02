package uzeduai.be.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.tenant.entity.TenantMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    @Query("""
            select tm from TenantMember tm
            join fetch tm.tenant
            where tm.user.id = :userId
            """)
    List<TenantMember> findByUserIdWithTenant(@Param("userId") UUID userId);

    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
