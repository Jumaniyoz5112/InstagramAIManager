package uzeduai.be.instagram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uzeduai.be.common.crypto.EncryptedStringConverter;
import uzeduai.be.instagram.domain.InstagramAccountStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instagram_accounts")
@Getter
@Setter
public class InstagramAccount {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "ig_user_id", nullable = false, length = 64)
    private String igUserId;

    @Column(length = 255)
    private String username;

    @Column(name = "account_type", length = 40)
    private String accountType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(length = 512)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InstagramAccountStatus status = InstagramAccountStatus.ACTIVE;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

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
