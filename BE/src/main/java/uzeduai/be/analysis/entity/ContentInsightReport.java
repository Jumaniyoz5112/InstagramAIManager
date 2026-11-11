package uzeduai.be.analysis.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uzeduai.be.analysis.domain.InsightJobStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "content_insight_reports")
@Getter
@Setter
public class ContentInsightReport {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "instagram_account_id")
    private UUID instagramAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InsightJobStatus status = InsightJobStatus.QUEUED;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(name = "schema_version", length = 16)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

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
