package uzeduai.be.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
