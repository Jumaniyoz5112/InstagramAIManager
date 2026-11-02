package uzeduai.be.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.workspace.entity.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    List<Workspace> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);
    Optional<Workspace> findByIdAndTenantId(UUID id, UUID tenantId);
}
