package uzeduai.be.instagram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uzeduai.be.instagram.domain.SyncJobStatus;
import uzeduai.be.instagram.domain.SyncJobType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_jobs")
@Getter
@Setter
public class SyncJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "instagram_account_id", nullable = false)
    private UUID instagramAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private SyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncJobStatus status = SyncJobStatus.PENDING;

    @Column(name = "cursor_after", length = 512)
    private String cursorAfter;

    @Column(name = "pages_fetched", nullable = false)
    private int pagesFetched;

    @Column(name = "items_upserted", nullable = false)
    private int itemsUpserted;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
