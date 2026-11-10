package uzeduai.be.instagram.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.common.response.PageResponse;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.dto.InstagramMediaChildResponse;
import uzeduai.be.instagram.dto.InstagramMediaCommentResponse;
import uzeduai.be.instagram.dto.InstagramMediaResponse;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.repository.InstagramMediaChildRepository;
import uzeduai.be.instagram.repository.InstagramMediaCommentRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.tenant.context.TenantMembershipService;

import java.util.List;
import java.util.UUID;

@Service
public class InstagramMediaService {

    private final InstagramMediaRepository mediaRepository;
    private final InstagramMediaChildRepository childRepository;
    private final InstagramMediaCommentRepository commentRepository;
    private final InstagramMediaUrlService mediaUrlService;
    private final TenantMembershipService tenantMembershipService;

    public InstagramMediaService(
            InstagramMediaRepository mediaRepository,
            InstagramMediaChildRepository childRepository,
            InstagramMediaCommentRepository commentRepository,
            InstagramMediaUrlService mediaUrlService,
            TenantMembershipService tenantMembershipService
    ) {
        this.mediaRepository = mediaRepository;
        this.childRepository = childRepository;
        this.commentRepository = commentRepository;
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
        List<InstagramMediaResponse> content = result.getContent().stream()
                .filter(m -> m.getWorkspaceId().equals(principal.getWorkspaceId()))
                .map(m -> toResponse(m, false))
                .toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public InstagramMediaResponse get(UUID mediaId, CustomUserDetails principal) {
        InstagramMedia media = requireMedia(mediaId, principal);
        media = mediaUrlService.refreshUrlIfExpired(media);
        return toResponse(media, true);
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

    private InstagramMediaResponse toResponse(InstagramMedia media, boolean includeChildren) {
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
