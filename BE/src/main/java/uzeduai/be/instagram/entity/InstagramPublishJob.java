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
import uzeduai.be.instagram.domain.PublishJobStatus;
import uzeduai.be.instagram.domain.PublishType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instagram_publish_jobs")
@Getter
@Setter
public class InstagramPublishJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "instagram_account_id", nullable = false)
    private UUID instagramAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_type", nullable = false, length = 32)
    private PublishType publishType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublishJobStatus status = PublishJobStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Column(name = "published_ig_media_id", length = 64)
    private String publishedIgMediaId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

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
