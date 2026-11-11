package uzeduai.be.instagram.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.analysis.domain.AnalysisKind;
import uzeduai.be.analysis.entity.ContentAnalysis;
import uzeduai.be.analysis.entity.ContentAnalysisResult;
import uzeduai.be.analysis.repository.ContentAnalysisRepository;
import uzeduai.be.analysis.repository.ContentAnalysisResultRepository;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.common.response.PageResponse;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;
import uzeduai.be.instagram.dto.InstagramMediaChildResponse;
import uzeduai.be.instagram.dto.InstagramMediaCommentResponse;
import uzeduai.be.instagram.dto.InstagramMediaResponse;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.repository.InstagramMediaChildRepository;
import uzeduai.be.instagram.repository.InstagramMediaCommentRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InstagramMediaService {

    private final InstagramMediaRepository mediaRepository;
    private final InstagramMediaChildRepository childRepository;
    private final InstagramMediaCommentRepository commentRepository;
    private final ContentAnalysisRepository analysisRepository;
    private final ContentAnalysisResultRepository analysisResultRepository;
    private final InstagramMediaUrlService mediaUrlService;
    private final TenantMembershipService tenantMembershipService;

    public InstagramMediaService(
            InstagramMediaRepository mediaRepository,
            InstagramMediaChildRepository childRepository,
            InstagramMediaCommentRepository commentRepository,
            ContentAnalysisRepository analysisRepository,
            ContentAnalysisResultRepository analysisResultRepository,
            InstagramMediaUrlService mediaUrlService,
            TenantMembershipService tenantMembershipService
    ) {
        this.mediaRepository = mediaRepository;
        this.childRepository = childRepository;
        this.commentRepository = commentRepository;
        this.analysisRepository = analysisRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.mediaUrlService = mediaUrlService;
        this.tenantMembershipService = tenantMembershipService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InstagramMediaResponse> list(
            CustomUserDetails principal,
            UUID accountId,
            InstagramMediaType mediaType,
            int page,
            int size
    ) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<InstagramMedia> result;
        if (accountId != null) {
            result = mediaType == null
                    ? mediaRepository.findByTenantIdAndInstagramAccountIdOrderByPublishedAtDesc(
                            principal.getTenantId(), accountId, pageable)
                    : mediaRepository.findByTenantIdAndInstagramAccountIdAndMediaTypeOrderByPublishedAtDesc(
                            principal.getTenantId(), accountId, mediaType, pageable);
        } else {
            result = mediaType == null
                    ? mediaRepository.findByTenantIdAndWorkspaceIdOrderByPublishedAtDesc(
                            principal.getTenantId(), principal.getWorkspaceId(), pageable)
                    : mediaRepository.findByTenantIdAndWorkspaceIdAndMediaTypeOrderByPublishedAtDesc(
                            principal.getTenantId(), principal.getWorkspaceId(), mediaType, pageable);
        }
        List<InstagramMedia> pageItems = result.getContent().stream()
                .filter(m -> m.getWorkspaceId().equals(principal.getWorkspaceId()))
                .toList();
        Map<UUID, String> summaries = loadAnalysisSummaries(pageItems);
        List<InstagramMediaResponse> content = pageItems.stream()
                .map(m -> toResponse(m, false, summaries.get(m.getId())))
                .toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public InstagramMediaResponse get(UUID mediaId, CustomUserDetails principal) {
        InstagramMedia media = requireMedia(mediaId, principal);
        media = mediaUrlService.refreshUrlIfExpired(media);
        Map<UUID, String> summaries = loadAnalysisSummaries(List.of(media));
        return toResponse(media, true, summaries.get(media.getId()));
    }

    @Transactional(readOnly = true)
    public List<InstagramMediaCommentResponse> listComments(UUID mediaId, CustomUserDetails principal) {
        InstagramMedia media = requireMedia(mediaId, principal);
        return commentRepository
                .findByTenantIdAndInstagramMediaIdOrderByPublishedAtAsc(principal.getTenantId(), media.getId())
                .stream()
                .map(c -> new InstagramMediaCommentResponse(
                        c.getId(),
                        c.getIgCommentId(),
                        c.getParentIgCommentId(),
                        c.getUsername(),
                        c.getText(),
                        c.getLikeCount(),
                        c.getPublishedAt()
                ))
                .toList();
    }

    private Map<UUID, String> loadAnalysisSummaries(List<InstagramMedia> mediaList) {
        List<UUID> analyzedIds = mediaList.stream()
                .filter(m -> m.getAnalysisStatus() == MediaAnalysisStatus.ANALYZED)
                .map(InstagramMedia::getId)
                .toList();
        if (analyzedIds.isEmpty()) {
            return Map.of();
        }
        List<ContentAnalysis> jobs = analysisRepository.findByInstagramMediaIdInAndAnalysisKind(
                analyzedIds, AnalysisKind.CONTENT);
        if (jobs.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> analysisIdByMedia = jobs.stream()
                .collect(Collectors.toMap(ContentAnalysis::getInstagramMediaId, ContentAnalysis::getId, (a, b) -> a));
        List<ContentAnalysisResult> results = analysisResultRepository.findByContentAnalysisIdIn(
                analysisIdByMedia.values().stream().toList());
        Map<UUID, Map<String, Object>> jsonByAnalysisId = results.stream()
                .collect(Collectors.toMap(
                        ContentAnalysisResult::getContentAnalysisId,
                        ContentAnalysisResult::getResultJson,
                        (a, b) -> a
                ));

        Map<UUID, String> out = new HashMap<>();
        for (Map.Entry<UUID, UUID> e : analysisIdByMedia.entrySet()) {
            String summary = extractSummary(jsonByAnalysisId.get(e.getValue()));
            if (summary != null) {
                out.put(e.getKey(), summary);
            }
        }
        return out;
    }

    private static String extractSummary(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        Object summary = result.get("correctedSummary");
        if (summary == null || summary.toString().isBlank()) {
            summary = result.get("summaryUz");
        }
        if (summary == null || summary.toString().isBlank()) {
            summary = result.get("mainTopic");
        }
        if (summary == null || summary.toString().isBlank()) {
            return null;
        }
        String text = summary.toString().trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private InstagramMedia requireMedia(UUID mediaId, CustomUserDetails principal) {
        requireWorkspace(principal);
        tenantMembershipService.requireMembership(principal.getTenantId(), principal.getId());
        InstagramMedia media = mediaRepository.findByIdAndTenantId(mediaId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_MEDIA_NOT_FOUND));
        if (!media.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        return media;
    }

    private InstagramMediaResponse toResponse(InstagramMedia media, boolean includeChildren, String analysisSummary) {
        List<InstagramMediaChildResponse> children = List.of();
        if (includeChildren) {
            children = childRepository.findByParentMediaIdOrderByPositionAsc(media.getId()).stream()
                    .map(c -> new InstagramMediaChildResponse(
                            c.getId(),
                            c.getIgMediaId(),
                            c.getMediaType(),
                            c.getMediaUrl(),
                            c.getThumbnailUrl(),
                            c.getPosition()
                    ))
                    .toList();
        }
        return new InstagramMediaResponse(
                media.getId(),
                media.getInstagramAccountId(),
                media.getIgMediaId(),
                media.getMediaType(),
                media.getCaption(),
                media.getMediaUrl(),
                media.getThumbnailUrl(),
                media.getPermalink(),
                media.getPublishedAt(),
                media.getAnalysisStatus(),
                analysisSummary,
                media.getLikeCount(),
                media.getCommentsCount(),
                media.getSharesCount(),
                media.getSavesCount(),
                children
        );
    }

    private void requireWorkspace(CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
    }
}
