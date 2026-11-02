package uzeduai.be.tenant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uzeduai.be.tenant.entity.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsBySlug(String slug);

    Optional<Tenant> findBySlug(String slug);

    @Query("""
            select t from Tenant t
            where (:q is null or :q = ''
               or lower(t.name) like lower(concat('%', cast(:q as string), '%'))
               or lower(t.slug) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Tenant> search(@Param("q") String q, Pageable pageable);
}
