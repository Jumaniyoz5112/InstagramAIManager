package uzeduai.be.access.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.access.entity.UserPageAccess;

import java.util.List;
import java.util.UUID;

public interface UserPageAccessRepository extends JpaRepository<UserPageAccess, UUID> {

    List<UserPageAccess> findByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserPageAccess u where u.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
