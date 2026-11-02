package uzeduai.be.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_pages")
@Getter
@Setter
public class AppPage {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 120)
    private String path;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "default_for_new_users", nullable = false)
    private boolean defaultForNewUsers;

    @Column(name = "admin_only", nullable = false)
    private boolean adminOnly;

    @Column(nullable = false)
    private boolean enabled = true;
}
