package uzeduai.be.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uzeduai.be.analysis.domain.EngagementSlot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "media_engagement_snapshots")
@Getter
@Setter
public class MediaEngagementSnapshot {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "instagram_media_id", nullable = false)
    private UUID instagramMediaId;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "comments_count")
    private Integer commentsCount;

    @Column(name = "shares_count")
    private Integer sharesCount;

    @Column(name = "saves_count")
    private Integer savesCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comments_sample", columnDefinition = "jsonb")
    private List<Map<String, Object>> commentsSample;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EngagementSlot slot;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (capturedAt == null) {
            capturedAt = now;
        }
        createdAt = now;
    }
}
