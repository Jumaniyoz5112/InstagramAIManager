package uzeduai.be.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.workspace.entity.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    List<Workspace> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    long countByTenantId(UUID tenantId);

    Optional<Workspace> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select w from Workspace w
            join fetch w.tenant
            where w.id = :id
            """)
    Optional<Workspace> findByIdWithTenant(@Param("id") UUID id);
}
