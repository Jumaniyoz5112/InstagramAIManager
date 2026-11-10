package uzeduai.be.instagram.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.domain.PublishJobStatus;
import uzeduai.be.instagram.domain.PublishType;
import uzeduai.be.instagram.dto.InstagramPublishJobResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramPublishAsset;
import uzeduai.be.instagram.entity.InstagramPublishJob;
import uzeduai.be.instagram.repository.InstagramPublishAssetRepository;
import uzeduai.be.instagram.repository.InstagramPublishJobRepository;
import uzeduai.be.security.user.CustomUserDetails;
import uzeduai.be.storage.StoragePort;
import uzeduai.be.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InstagramPublishService {

    private static final Logger log = LoggerFactory.getLogger(InstagramPublishService.class);

    private final InstagramProperties properties;
    private final InstagramGraphClient graphClient;
    private final InstagramAccountService accountService;
    private final InstagramPublishJobRepository jobRepository;
    private final InstagramPublishAssetRepository assetRepository;
    private final StoragePort storagePort;
    private final PublishMediaUrlSigner urlSigner;

    public InstagramPublishService(
            InstagramProperties properties,
            InstagramGraphClient graphClient,
            InstagramAccountService accountService,
            InstagramPublishJobRepository jobRepository,
            InstagramPublishAssetRepository assetRepository,
            StoragePort storagePort,
            PublishMediaUrlSigner urlSigner
    ) {
        this.properties = properties;
        this.graphClient = graphClient;
        this.accountService = accountService;
        this.jobRepository = jobRepository;
        this.assetRepository = assetRepository;
        this.storagePort = storagePort;
        this.urlSigner = urlSigner;
    }

    @Transactional(readOnly = true)
    public InstagramPublishJobResponse get(UUID jobId, CustomUserDetails principal) {
        InstagramPublishJob job = requireJob(jobId, principal);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<InstagramPublishJobResponse> list(UUID accountId, CustomUserDetails principal) {
        InstagramAccount account = accountService.requireAccountForUser(accountId, principal);
        return jobRepository
                .findByTenantIdAndWorkspaceIdAndInstagramAccountIdOrderByCreatedAtDesc(
                        principal.getTenantId(),
                        principal.getWorkspaceId(),
                        account.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Not one long @Transactional — Meta container polling can take minutes. */
    public InstagramPublishJobResponse publish(
            UUID accountId,
            PublishType publishType,
            String caption,
            String idempotencyKey,
            List<MultipartFile> files,
            CustomUserDetails principal
    ) {
        requireEnabled();
        InstagramAccount account = requireActiveAccount(accountId, principal);

        String normalizedKey = blankToNull(idempotencyKey);
        if (normalizedKey != null) {
            var existing = jobRepository.findByTenantIdAndIdempotencyKey(principal.getTenantId(), normalizedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        validateFiles(publishType, files);
        enforcePublishQuota(account);

        InstagramPublishJob job = new InstagramPublishJob();
        job.setTenantId(principal.getTenantId());
        job.setWorkspaceId(principal.getWorkspaceId());
        job.setInstagramAccountId(account.getId());
        job.setPublishType(publishType);
        job.setStatus(PublishJobStatus.UPLOADING);
        job.setCaption(blankToNull(caption));
        job.setIdempotencyKey(normalizedKey);
        job.setCreatedByUserId(principal.getId());
        job.setStartedAt(Instant.now());
        job = jobRepository.save(job);

        try {
            List<InstagramPublishAsset> assets = storeFiles(job, files);
            if (assets.stream().anyMatch(a -> a.getPublicUrl() == null || a.getPublicUrl().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_PUBLIC_URL_REQUIRED);
            }

            String publishedId = executePublish(account, job, assets);
            job.setPublishedIgMediaId(publishedId);
            job.setStatus(PublishJobStatus.PUBLISHED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(null);
            return toResponse(jobRepository.save(job));
        } catch (ApiException ex) {
            failJob(job, ex.getMessageKey());
            throw ex;
        } catch (Exception ex) {
            log.warn("Instagram publish failed jobId={}: {}", job.getId(), ex.getMessage());
            failJob(job, truncate(ex.getMessage()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.INSTAGRAM_PUBLISH_FAILED, truncate(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public InstagramPublishAsset requireAsset(UUID assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_PUBLISH_ASSET_NOT_FOUND));
    }

    private String executePublish(
            InstagramAccount account,
            InstagramPublishJob job,
            List<InstagramPublishAsset> assets
    ) throws InterruptedException {
        String token = account.getAccessToken();
        String igUserId = account.getIgUserId();

        return switch (job.getPublishType()) {
            case FEED_IMAGE -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("image_url", assets.getFirst().getPublicUrl());
                if (job.getCaption() != null) {
                    params.put("caption", job.getCaption());
                }
                String containerId = graphClient.createMediaContainer(igUserId, token, params);
                job.setContainerId(containerId);
                job.setStatus(PublishJobStatus.CONTAINER_CREATED);
                jobRepository.save(job);
                waitUntilFinished(containerId, token, job);
                job.setStatus(PublishJobStatus.PUBLISHING);
                jobRepository.save(job);
                yield graphClient.publishMedia(igUserId, token, containerId);
            }
            case REELS -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("media_type", "REELS");
                params.put("video_url", assets.getFirst().getPublicUrl());
                if (job.getCaption() != null) {
                    params.put("caption", job.getCaption());
                }
                String containerId = graphClient.createMediaContainer(igUserId, token, params);
                job.setContainerId(containerId);
                job.setStatus(PublishJobStatus.CONTAINER_CREATED);
                jobRepository.save(job);
                waitUntilFinished(containerId, token, job);
                job.setStatus(PublishJobStatus.PUBLISHING);
                jobRepository.save(job);
                yield graphClient.publishMedia(igUserId, token, containerId);
            }
            case STORY_IMAGE -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("media_type", "STORIES");
                params.put("image_url", assets.getFirst().getPublicUrl());
                String containerId = graphClient.createMediaContainer(igUserId, token, params);
                job.setContainerId(containerId);
                job.setStatus(PublishJobStatus.CONTAINER_CREATED);
                jobRepository.save(job);
                waitUntilFinished(containerId, token, job);
                job.setStatus(PublishJobStatus.PUBLISHING);
                jobRepository.save(job);
                yield graphClient.publishMedia(igUserId, token, containerId);
            }
            case STORY_VIDEO -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("media_type", "STORIES");
                params.put("video_url", assets.getFirst().getPublicUrl());
                String containerId = graphClient.createMediaContainer(igUserId, token, params);
                job.setContainerId(containerId);
                job.setStatus(PublishJobStatus.CONTAINER_CREATED);
                jobRepository.save(job);
                waitUntilFinished(containerId, token, job);
                job.setStatus(PublishJobStatus.PUBLISHING);
                jobRepository.save(job);
                yield graphClient.publishMedia(igUserId, token, containerId);
            }
            case CAROUSEL -> {
                List<String> childIds = new ArrayList<>();
                for (InstagramPublishAsset asset : assets) {
                    Map<String, String> childParams = new LinkedHashMap<>();
                    childParams.put("is_carousel_item", "true");
                    if (isVideo(asset.getContentType(), asset.getOriginalFilename())) {
                        childParams.put("media_type", "VIDEO");
                        childParams.put("video_url", asset.getPublicUrl());
                    } else {
                        childParams.put("image_url", asset.getPublicUrl());
                    }
                    String childId = graphClient.createMediaContainer(igUserId, token, childParams);
                    asset.setChildContainerId(childId);
                    assetRepository.save(asset);
                    if (isVideo(asset.getContentType(), asset.getOriginalFilename())) {
                        waitUntilFinished(childId, token, job);
                    }
                    childIds.add(childId);
                }
                Map<String, String> parentParams = new LinkedHashMap<>();
                parentParams.put("media_type", "CAROUSEL");
                parentParams.put("children", String.join(",", childIds));
                if (job.getCaption() != null) {
                    parentParams.put("caption", job.getCaption());
                }
                String containerId = graphClient.createMediaContainer(igUserId, token, parentParams);
                job.setContainerId(containerId);
                job.setStatus(PublishJobStatus.CONTAINER_CREATED);
                jobRepository.save(job);
                waitUntilFinished(containerId, token, job);
                job.setStatus(PublishJobStatus.PUBLISHING);
                jobRepository.save(job);
                yield graphClient.publishMedia(igUserId, token, containerId);
            }
        };
    }

    private void waitUntilFinished(String containerId, String accessToken, InstagramPublishJob job)
            throws InterruptedException {
        job.setStatus(PublishJobStatus.PROCESSING);
        jobRepository.save(job);
        int attempts = Math.max(1, properties.getPublishPollMaxAttempts());
        int interval = Math.max(500, properties.getPublishPollIntervalMs());
        for (int i = 0; i < attempts; i++) {
            InstagramGraphClient.ContainerStatus status = graphClient.fetchContainerStatus(containerId, accessToken);
            String code = status.statusCode() == null ? "" : status.statusCode().toUpperCase(Locale.ROOT);
            if ("FINISHED".equals(code) || "PUBLISHED".equals(code)) {
                return;
            }
            if ("ERROR".equals(code) || "EXPIRED".equals(code)) {
                String detail = status.status() == null ? code : status.status();
                throw new IllegalStateException("Container status " + detail);
            }
            Thread.sleep(interval);
        }
        throw new IllegalStateException("Container processing timed out: " + containerId);
    }

    private List<InstagramPublishAsset> storeFiles(InstagramPublishJob job, List<MultipartFile> files) {
        List<InstagramPublishAsset> assets = new ArrayList<>();
        int position = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            try (InputStream in = file.getInputStream()) {
                StoredObject stored = storagePort.store(
                        job.getTenantId(),
                        job.getWorkspaceId(),
                        "instagram-publish",
                        filename,
                        in,
                        file.getSize()
                );
                InstagramPublishAsset asset = new InstagramPublishAsset();
                asset.setTenantId(job.getTenantId());
                asset.setPublishJobId(job.getId());
                asset.setPosition(position++);
                asset.setContentType(file.getContentType());
                asset.setOriginalFilename(filename);
                asset.setStorageKey(stored.storageKey());
                asset.setSizeBytes(stored.sizeBytes());
                asset = assetRepository.save(asset);
                asset.setPublicUrl(urlSigner.buildPublicUrl(asset.getId()));
                assets.add(assetRepository.save(asset));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to store upload: " + filename, ex);
            }
        }
        if (assets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
        return assets;
    }

    private void validateFiles(PublishType type, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
        List<MultipartFile> nonEmpty = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (nonEmpty.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
        switch (type) {
            case FEED_IMAGE, STORY_IMAGE -> {
                if (nonEmpty.size() != 1) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
                }
                requireImage(nonEmpty.getFirst());
            }
            case REELS, STORY_VIDEO -> {
                if (nonEmpty.size() != 1) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
                }
                requireVideo(nonEmpty.getFirst());
            }
            case CAROUSEL -> {
                if (nonEmpty.size() < 2 || nonEmpty.size() > 10) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
                }
                for (MultipartFile file : nonEmpty) {
                    if (isVideo(file.getContentType(), file.getOriginalFilename())) {
                        requireVideo(file);
                    } else {
                        requireImage(file);
                    }
                }
            }
        }
    }

    private void requireImage(MultipartFile file) {
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean jpeg = ct.contains("jpeg") || ct.contains("jpg") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        if (!jpeg) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
        if (file.getSize() > properties.getPublishMaxImageBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
    }

    private void requireVideo(MultipartFile file) {
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean ok = ct.contains("mp4") || ct.contains("quicktime") || name.endsWith(".mp4") || name.endsWith(".mov");
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
        if (file.getSize() > properties.getPublishMaxVideoBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_INVALID);
        }
    }

    private static boolean isVideo(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return ct.contains("mp4") || ct.contains("quicktime") || ct.contains("video")
                || name.endsWith(".mp4") || name.endsWith(".mov");
    }

    private void enforcePublishQuota(InstagramAccount account) {
        try {
            InstagramGraphClient.PublishingLimit limit =
                    graphClient.fetchContentPublishingLimit(account.getIgUserId(), account.getAccessToken());
            if (limit.quotaUsage() >= limit.quotaTotal()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, MessageKeys.INSTAGRAM_PUBLISH_LIMIT);
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("content_publishing_limit check skipped: {}", ex.getMessage());
        }
    }

    private InstagramAccount requireActiveAccount(UUID accountId, CustomUserDetails principal) {
        InstagramAccount account = accountService.requireAccountForUser(accountId, principal);
        if (account.getStatus() != InstagramAccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_PUBLISH_ACCOUNT_INACTIVE);
        }
        return account;
    }

    private InstagramPublishJob requireJob(UUID jobId, CustomUserDetails principal) {
        if (principal.getTenantId() == null || principal.getWorkspaceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MessageKeys.INSTAGRAM_WORKSPACE_REQUIRED);
        }
        InstagramPublishJob job = jobRepository.findByIdAndTenantId(jobId, principal.getTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MessageKeys.INSTAGRAM_PUBLISH_NOT_FOUND));
        if (!job.getWorkspaceId().equals(principal.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MessageKeys.WORKSPACE_NOT_MEMBER);
        }
        return job;
    }

    private void failJob(InstagramPublishJob job, String message) {
        job.setStatus(PublishJobStatus.FAILED);
        job.setErrorMessage(truncate(message));
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.INSTAGRAM_DISABLED);
        }
    }

    private InstagramPublishJobResponse toResponse(InstagramPublishJob job) {
        return new InstagramPublishJobResponse(
                job.getId(),
                job.getInstagramAccountId(),
                job.getPublishType(),
                job.getStatus(),
                job.getCaption(),
                job.getContainerId(),
                job.getPublishedIgMediaId(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
