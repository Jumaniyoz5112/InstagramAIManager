package uzeduai.be.analysis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.analysis.client.AiAnalysisClient;
import uzeduai.be.analysis.config.AiServiceProperties;
import uzeduai.be.analysis.domain.AnalysisJobStatus;
import uzeduai.be.analysis.domain.AnalysisKind;
import uzeduai.be.analysis.dto.AnalysisActionResponse;
import uzeduai.be.analysis.dto.AnalysisFeedbackRequest;
import uzeduai.be.analysis.dto.ContentAnalysisResponse;
import uzeduai.be.analysis.entity.ContentAnalysis;
import uzeduai.be.analysis.entity.ContentAnalysisResult;
import uzeduai.be.analysis.repository.ContentAnalysisRepository;
import uzeduai.be.analysis.repository.ContentAnalysisResultRepository;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.entity.InstagramMediaChild;
import uzeduai.be.instagram.repository.InstagramMediaChildRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ContentAnalysisService.class);

    private final AiServiceProperties properties;
    private final AiAnalysisClient aiClient;
    private final ContentAnalysisRepository analysisRepository;
    private final ContentAnalysisResultRepository resultRepository;
    private final InstagramMediaRepository mediaRepository;
    private final InstagramMediaChildRepository childRepository;
    private final TenantMembershipService tenantMembershipService;
    private final ContentAnalysisService self;

    public ContentAnalysisService(
            AiServiceProperties properties,
            AiAnalysisClient aiClient,
            ContentAnalysisRepository analysisRepository,
            ContentAnalysisResultRepository resultRepository,
            InstagramMediaRepository mediaRepository,
            InstagramMediaChildRepository childRepository,
            TenantMembershipService tenantMembershipService,
            @Lazy ContentAnalysisService self
    ) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.analysisRepository = analysisRepository;
        this.resultRepository = resultRepository;
        this.mediaRepository = mediaRepository;
        this.childRepository = childRepository;
        this.tenantMembershipService = tenantMembershipService;
        this.self = self;
    }

    /**
     * Manual enqueue from UI. Idempotent for QUEUED/RUNNING.
     * Re-queues FAILED / SKIPPED / NOT_ANALYZED / ANALYZED (explicit re-run, e.g. new prompt).
     */
    @Transactional
    public boolean enqueueIfNeeded(InstagramMedia media) {
        if (media == null || media.getId() == null) {
            return false;
        }
        var existing = analysisRepository.findByInstagramMediaIdAndAnalysisKind(media.getId(), AnalysisKind.CONTENT);
        if (existing.isPresent()) {
            AnalysisJobStatus status = existing.get().getStatus();
            if (status == AnalysisJobStatus.QUEUED || status == AnalysisJobStatus.RUNNING) {
                return false;
            }
            ContentAnalysis job = existing.get();
            job.setStatus(AnalysisJobStatus.QUEUED);
            job.setErrorMessage(null);
            job.setStartedAt(null);
            job.setFinishedAt(null);
            job.setPromptVersion(properties.getPromptVersion());
            job.setModelName(properties.getModelName());
            analysisRepository.save(job);
            media.setAnalysisStatus(MediaAnalysisStatus.QUEUED);
            mediaRepository.save(media);
            return true;
        }

        ContentAnalysis job = new ContentAnalysis();
        job.setTenantId(media.getTenantId());
        job.setWorkspaceId(media.getWorkspaceId());
        job.setInstagramMediaId(media.getId());
        job.setAnalysisKind(AnalysisKind.CONTENT);
        job.setStatus(AnalysisJobStatus.QUEUED);
        job.setPromptVersion(properties.getPromptVersion());
        job.setModelName(properties.getModelName());
        analysisRepository.save(job);

        media.setAnalysisStatus(MediaAnalysisStatus.QUEUED);
        mediaRepository.save(media);
        return true;
    }

    @Transactional
    public AnalysisActionResponse enqueueSelected(List<UUID> mediaIds, CustomUserDetails principal) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.ANALYSIS_DISABLED);
        }
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());

        int queued = 0;
        int ignored = 0;
        List<UUID> acted = new ArrayList<>();
        for (UUID mediaId : mediaIds) {
            InstagramMedia media = mediaRepository.findByIdAndTenantId(mediaId, principal.getTenantId()).orElse(null);
            if (media == null || !media.getWorkspaceId().equals(principal.getWorkspaceId())) {
                ignored++;
                continue;
            }
            if (enqueueIfNeeded(media)) {
                queued++;
                acted.add(mediaId);
            } else {
                ignored++;
            }
        }
        return new AnalysisActionResponse(queued, 0, ignored, acted);
    }

    @Transactional
    public AnalysisActionResponse skipSelected(List<UUID> mediaIds, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());

        int skipped = 0;
        int ignored = 0;
        List<UUID> acted = new ArrayList<>();
        for (UUID mediaId : mediaIds) {
            InstagramMedia media = mediaRepository.findByIdAndTenantId(mediaId, principal.getTenantId()).orElse(null);
            if (media == null || !media.getWorkspaceId().equals(principal.getWorkspaceId())) {
                ignored++;
                continue;
            }
            MediaAnalysisStatus status = media.getAnalysisStatus();
            if (status == MediaAnalysisStatus.ANALYZED
                    || status == MediaAnalysisStatus.RUNNING
                    || status == MediaAnalysisStatus.SKIPPED) {
                ignored++;
                continue;
            }
            // Cancel pending job if any
            analysisRepository.findByInstagramMediaIdAndAnalysisKind(media.getId(), AnalysisKind.CONTENT)
                    .ifPresent(job -> {
                        if (job.getStatus() == AnalysisJobStatus.QUEUED
                                || job.getStatus() == AnalysisJobStatus.FAILED) {
                            analysisRepository.delete(job);
                        }
                    });
            media.setAnalysisStatus(MediaAnalysisStatus.SKIPPED);
            mediaRepository.save(media);
            skipped++;
            acted.add(mediaId);
        }
        return new AnalysisActionResponse(0, skipped, ignored, acted);
    }

    @Transactional
    public void processQueuedJobs() {
        if (!properties.isEnabled()) {
            return;
        }
        // Only process explicitly queued jobs (manual UI selection) — never auto-pick NOT_ANALYZED.

        long running = analysisRepository.findByStatusOrderByCreatedAtAsc(AnalysisJobStatus.RUNNING).size();
        int slots = Math.max(1, properties.getMaxConcurrent()) - (int) running;
        if (slots <= 0) {
            return;
        }

        List<ContentAnalysis> queued = analysisRepository.findByStatusOrderByCreatedAtAsc(AnalysisJobStatus.QUEUED)
                .stream()
                .limit(slots)
                .toList();
        for (ContentAnalysis job : queued) {
            self.processOne(job.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(UUID analysisId) {
        ContentAnalysis job = analysisRepository.findById(analysisId).orElse(null);
        if (job == null || job.getStatus() != AnalysisJobStatus.QUEUED) {
            return;
        }
        InstagramMedia media = mediaRepository.findById(job.getInstagramMediaId()).orElse(null);
        if (media == null) {
            job.setStatus(AnalysisJobStatus.FAILED);
            job.setErrorMessage("Media not found");
            job.setFinishedAt(Instant.now());
            analysisRepository.save(job);
            return;
        }

        job.setStatus(AnalysisJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setModelName(properties.getModelName());
        job.setPromptVersion(properties.getPromptVersion());
        analysisRepository.save(job);
        media.setAnalysisStatus(MediaAnalysisStatus.RUNNING);
        mediaRepository.save(media);

        try {
            List<String> imageUrls = resolveImageUrls(media);
            if (imageUrls.isEmpty()) {
                throw new IllegalStateException("No image/thumbnail URL available for analysis");
            }
            AiAnalysisClient.AnalyzeResponse response = aiClient.analyze(new AiAnalysisClient.AnalyzeRequest(
                    media.getId().toString(),
                    media.getMediaType().name(),
                    media.getCaption(),
                    imageUrls
            ));

            ContentAnalysisResult result = resultRepository.findByContentAnalysisId(job.getId())
                    .orElseGet(ContentAnalysisResult::new);
            result.setContentAnalysisId(job.getId());
            result.setTenantId(job.getTenantId());
            result.setSchemaVersion(response.schemaVersion());
            result.setResultJson(response.result());
            resultRepository.save(result);

            job.setStatus(AnalysisJobStatus.ANALYZED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(null);
            job.setModelName(response.model());
            analysisRepository.save(job);

            media.setAnalysisStatus(MediaAnalysisStatus.ANALYZED);
            mediaRepository.save(media);
            log.info("Content analysis done for media {}", media.getId());
        } catch (Exception e) {
            log.warn("Content analysis failed for media {}: {}", media.getId(), e.getMessage());
            job.setStatus(AnalysisJobStatus.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(trim(e.getMessage(), 1000));
            analysisRepository.save(job);
            media.setAnalysisStatus(MediaAnalysisStatus.FAILED);
            mediaRepository.save(media);
        }
    }

    @Transactional(readOnly = true)
    public ContentAnalysisResponse getForMedia(UUID mediaId, CustomUserDetails principal) {
        ContentAnalysis job = requireJobForMedia(mediaId, principal);
        return toResponse(job);
    }

    /**
     * User correction stored inside result JSON (no schema ALTER): correctedSummary, userNote.
     */
    @Transactional
    public ContentAnalysisResponse saveUserFeedback(
            UUID mediaId,
            AnalysisFeedbackRequest request,
            CustomUserDetails principal
    ) {
        ContentAnalysis job = requireJobForMedia(mediaId, principal);
        if (job.getStatus() != AnalysisJobStatus.ANALYZED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.ANALYSIS_FEEDBACK_NOT_ALLOWED);
        }
        ContentAnalysisResult result = resultRepository.findByContentAnalysisId(job.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.ANALYSIS_NOT_FOUND));

        Map<String, Object> json = result.getResultJson() != null
                ? new HashMap<>(result.getResultJson())
                : new HashMap<>();

        String corrected = blankToNull(request.correctedSummary());
        String note = blankToNull(request.userNote());
        if (corrected != null) {
            json.put("correctedSummary", trim(corrected, 500));
        } else {
            json.remove("correctedSummary");
        }
        if (note != null) {
            json.put("userNote", trim(note, 500));
        } else {
            json.remove("userNote");
        }

        result.setResultJson(json);
        resultRepository.save(result);
        return toResponse(job);
    }

    private ContentAnalysis requireJobForMedia(UUID mediaId, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        InstagramMedia media = mediaRepository.findByIdAndTenantId(mediaId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_MEDIA_NOT_FOUND));
        if (!media.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        return analysisRepository
                .findByInstagramMediaIdAndTenantIdAndAnalysisKind(mediaId, principal.getTenantId(), AnalysisKind.CONTENT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.ANALYSIS_NOT_FOUND));
    }

    private ContentAnalysisResponse toResponse(ContentAnalysis job) {
        var result = resultRepository.findByContentAnalysisId(job.getId()).orElse(null);
        return new ContentAnalysisResponse(
                job.getId(),
                job.getInstagramMediaId(),
                job.getAnalysisKind(),
                job.getStatus(),
                job.getModelName(),
                job.getPromptVersion(),
                result != null ? result.getSchemaVersion() : null,
                result != null ? result.getResultJson() : null,
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> resolveImageUrls(InstagramMedia media) {
        List<String> urls = new ArrayList<>();
        if (media.getMediaType() == InstagramMediaType.CAROUSEL_ALBUM) {
            List<InstagramMediaChild> children = childRepository.findByParentMediaIdOrderByPositionAsc(media.getId());
            for (InstagramMediaChild child : children) {
                String url = firstNonBlank(child.getThumbnailUrl(), child.getMediaUrl());
                if (url != null && urls.size() < 4) {
                    urls.add(url);
                }
            }
        }
        if (urls.isEmpty()) {
            // IMAGE: media_url; VIDEO/REELS: prefer thumbnail (no full video re-encode in Phase-1)
            String url = media.getMediaType() == InstagramMediaType.IMAGE
                    ? firstNonBlank(media.getMediaUrl(), media.getThumbnailUrl())
                    : firstNonBlank(media.getThumbnailUrl(), media.getMediaUrl());
            if (url != null) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void requireWorkspace(CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
    }
}
