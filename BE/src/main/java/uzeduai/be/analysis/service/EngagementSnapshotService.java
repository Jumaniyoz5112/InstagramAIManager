package uzeduai.be.analysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.analysis.config.AiServiceProperties;
import uzeduai.be.analysis.domain.EngagementSlot;
import uzeduai.be.analysis.dto.EngagementSnapshotResponse;
import uzeduai.be.analysis.entity.MediaEngagementSnapshot;
import uzeduai.be.analysis.repository.MediaEngagementSnapshotRepository;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.entity.InstagramMediaComment;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramMediaCommentRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.instagram.service.InstagramSyncService;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EngagementSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(EngagementSnapshotService.class);
    private static final String MEDIA_FIELDS =
            "id,like_count,comments_count,shares_count,share_count,saved_count,saves_count";

    private final AiServiceProperties properties;
    private final InstagramSyncService syncService;
    private final InstagramGraphClient graphClient;
    private final InstagramAccountRepository accountRepository;
    private final InstagramMediaRepository mediaRepository;
    private final InstagramMediaCommentRepository commentRepository;
    private final MediaEngagementSnapshotRepository snapshotRepository;
    private final TenantMembershipService tenantMembershipService;

    public EngagementSnapshotService(
            AiServiceProperties properties,
            InstagramSyncService syncService,
            InstagramGraphClient graphClient,
            InstagramAccountRepository accountRepository,
            InstagramMediaRepository mediaRepository,
            InstagramMediaCommentRepository commentRepository,
            MediaEngagementSnapshotRepository snapshotRepository,
            TenantMembershipService tenantMembershipService
    ) {
        this.properties = properties;
        this.syncService = syncService;
        this.graphClient = graphClient;
        this.accountRepository = accountRepository;
        this.mediaRepository = mediaRepository;
        this.commentRepository = commentRepository;
        this.snapshotRepository = snapshotRepository;
        this.tenantMembershipService = tenantMembershipService;
    }

    @Transactional
    public void captureScheduledSlot() {
        ZoneId zone = ZoneId.of(properties.getEngagementZone());
        LocalTime now = LocalTime.now(zone);
        EngagementSlot slot = now.getHour() < 14 ? EngagementSlot.MORNING : EngagementSlot.EVENING;
        capture(slot);
    }

    @Transactional
    public void capture(EngagementSlot slot) {
        log.info("Engagement snapshot capture started slot={}", slot);
        try {
            syncService.runIncrementalForActiveAccounts();
        } catch (Exception e) {
            log.warn("Incremental sync before engagement capture failed: {}", e.getMessage());
        }

        int limit = Math.max(1, properties.getEngagementMediaLimitPerAccount());
        for (InstagramAccount account : accountRepository.findByStatus(InstagramAccountStatus.ACTIVE)) {
            try {
                captureForAccount(account, slot, limit);
            } catch (Exception e) {
                log.warn("Engagement capture failed for account {}: {}", account.getId(), e.getMessage());
            }
        }
        log.info("Engagement snapshot capture finished slot={}", slot);
    }

    private void captureForAccount(InstagramAccount account, EngagementSlot slot, int limit) {
        List<InstagramMedia> mediaList = mediaRepository
                .findByTenantIdAndInstagramAccountIdOrderByPublishedAtDesc(
                        account.getTenantId(),
                        account.getId(),
                        PageRequest.of(0, limit)
                )
                .getContent();

        Instant capturedAt = Instant.now();
        for (InstagramMedia media : mediaList) {
            refreshMetrics(account, media);
            refreshComments(account, media);

            List<Map<String, Object>> sample = commentRepository
                    .findByTenantIdAndInstagramMediaIdOrderByPublishedAtAsc(account.getTenantId(), media.getId())
                    .stream()
                    .limit(20)
                    .map(this::toCommentSample)
                    .toList();

            MediaEngagementSnapshot snap = new MediaEngagementSnapshot();
            snap.setTenantId(media.getTenantId());
            snap.setWorkspaceId(media.getWorkspaceId());
            snap.setInstagramMediaId(media.getId());
            snap.setLikeCount(media.getLikeCount());
            snap.setCommentsCount(media.getCommentsCount());
            snap.setSharesCount(media.getSharesCount());
            snap.setSavesCount(media.getSavesCount());
            snap.setCommentsSample(sample);
            snap.setSlot(slot);
            snap.setCapturedAt(capturedAt);
            snapshotRepository.save(snap);
        }
        mediaRepository.saveAll(mediaList);
    }

    private void refreshMetrics(InstagramAccount account, InstagramMedia media) {
        try {
            graphClient.fetchMediaFields(media.getIgMediaId(), account.getAccessToken(), MEDIA_FIELDS)
                    .ifPresent(fields -> {
                        media.setLikeCount(intVal(fields.get("like_count")));
                        media.setCommentsCount(intVal(fields.get("comments_count")));
                        Integer shares = intVal(firstNonNull(fields.get("shares_count"), fields.get("share_count")));
                        if (shares != null) {
                            media.setSharesCount(shares);
                        }
                        Integer saves = intVal(firstNonNull(fields.get("saved_count"), fields.get("saves_count")));
                        if (saves != null) {
                            media.setSavesCount(saves);
                        }
                        media.setInsightsSyncedAt(Instant.now());
                    });
        } catch (Exception e) {
            log.debug("Metrics refresh skipped for {}: {}", media.getIgMediaId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshComments(InstagramAccount account, InstagramMedia media) {
        List<Map<String, Object>> comments;
        try {
            comments = graphClient.fetchMediaComments(media.getIgMediaId(), account.getAccessToken());
        } catch (Exception e) {
            log.debug("Comments refresh skipped for {}: {}", media.getIgMediaId(), e.getMessage());
            return;
        }
        for (Map<String, Object> top : comments) {
            upsertComment(account.getTenantId(), media.getId(), top, null);
            Object repliesNode = top.get("replies");
            if (repliesNode instanceof Map<?, ?> repliesMap) {
                Object replyData = repliesMap.get("data");
                if (replyData instanceof List<?> replyList) {
                    for (Object replyItem : replyList) {
                        if (replyItem instanceof Map<?, ?> replyMap) {
                            upsertComment(
                                    account.getTenantId(),
                                    media.getId(),
                                    new LinkedHashMap<>((Map<String, Object>) replyMap),
                                    stringVal(top.get("id"))
                            );
                        }
                    }
                }
            }
        }
    }

    private void upsertComment(UUID tenantId, UUID mediaId, Map<String, Object> item, String parentIgCommentId) {
        String igCommentId = stringVal(item.get("id"));
        if (igCommentId == null) {
            return;
        }
        InstagramMediaComment comment = commentRepository
                .findByTenantIdAndIgCommentId(tenantId, igCommentId)
                .orElseGet(InstagramMediaComment::new);
        comment.setTenantId(tenantId);
        comment.setInstagramMediaId(mediaId);
        comment.setIgCommentId(igCommentId);
        comment.setParentIgCommentId(parentIgCommentId);
        comment.setUsername(stringVal(item.get("username")));
        comment.setText(stringVal(item.get("text")));
        comment.setLikeCount(intVal(item.get("like_count")));
        comment.setPublishedAt(parseInstant(stringVal(item.get("timestamp"))));
        comment.setRawPayload(item);
        commentRepository.save(comment);
    }

    private Map<String, Object> toCommentSample(InstagramMediaComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", c.getUsername());
        m.put("text", c.getText());
        m.put("likeCount", c.getLikeCount());
        m.put("publishedAt", c.getPublishedAt() != null ? c.getPublishedAt().toString() : null);
        return m;
    }

    @Transactional(readOnly = true)
    public List<EngagementSnapshotResponse> listForMedia(UUID mediaId, CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        InstagramMedia media = mediaRepository.findByIdAndTenantId(mediaId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_MEDIA_NOT_FOUND));
        if (!media.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        List<EngagementSnapshotResponse> out = new ArrayList<>();
        for (MediaEngagementSnapshot s : snapshotRepository
                .findByTenantIdAndInstagramMediaIdOrderByCapturedAtDesc(principal.getTenantId(), mediaId)) {
            out.add(new EngagementSnapshotResponse(
                    s.getId(),
                    s.getInstagramMediaId(),
                    s.getSlot(),
                    s.getLikeCount(),
                    s.getCommentsCount(),
                    s.getSharesCount(),
                    s.getSavesCount(),
                    s.getCommentsSample(),
                    s.getCapturedAt()
            ));
        }
        return out;
    }

    private static Integer intVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
