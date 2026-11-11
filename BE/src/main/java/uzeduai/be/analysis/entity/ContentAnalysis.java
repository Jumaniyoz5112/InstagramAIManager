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
import uzeduai.be.analysis.domain.AnalysisJobStatus;
import uzeduai.be.analysis.domain.AnalysisKind;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_analyses")
@Getter
@Setter
public class ContentAnalysis {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "instagram_media_id", nullable = false)
    private UUID instagramMediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisJobStatus status = AnalysisJobStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_kind", nullable = false, length = 32)
    private AnalysisKind analysisKind = AnalysisKind.CONTENT;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

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
