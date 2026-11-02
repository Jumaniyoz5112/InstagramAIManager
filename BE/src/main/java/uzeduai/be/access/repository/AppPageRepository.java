package uzeduai.be.access.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uzeduai.be.access.entity.AppPage;

import java.util.List;

public interface AppPageRepository extends JpaRepository<AppPage, String> {

    List<AppPage> findByEnabledTrueOrderBySortOrderAsc();

    List<AppPage> findByEnabledTrueAndDefaultForNewUsersTrueOrderBySortOrderAsc();
}
