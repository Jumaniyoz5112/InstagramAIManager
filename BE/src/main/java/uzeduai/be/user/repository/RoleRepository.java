package uzeduai.be.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.user.entity.Role;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
}
