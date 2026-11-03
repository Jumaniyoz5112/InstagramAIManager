package uzeduai.be.instagram.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uzeduai.be.common.exception.ApiException;
import uzeduai.be.common.i18n.MessageKeys;
import uzeduai.be.instagram.client.InstagramGraphClient;
import uzeduai.be.instagram.config.InstagramProperties;
import uzeduai.be.instagram.domain.InstagramAccountStatus;
import uzeduai.be.instagram.domain.InstagramMediaType;
import uzeduai.be.instagram.domain.MediaAnalysisStatus;
import uzeduai.be.instagram.domain.SyncJobStatus;
import uzeduai.be.instagram.domain.SyncJobType;
import uzeduai.be.instagram.dto.SyncJobResponse;
import uzeduai.be.instagram.entity.InstagramAccount;
import uzeduai.be.instagram.entity.InstagramMedia;
import uzeduai.be.instagram.entity.InstagramMediaChild;
import uzeduai.be.instagram.entity.SyncJob;
import uzeduai.be.instagram.repository.InstagramAccountRepository;
import uzeduai.be.instagram.repository.InstagramMediaChildRepository;
import uzeduai.be.instagram.repository.InstagramMediaRepository;
import uzeduai.be.instagram.repository.SyncJobRepository;
import uzeduai.be.security.user.CustomUserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InstagramSyncService {

    private static final Logger log = LoggerFactory.getLogger(InstagramSyncService.class);

    private final InstagramProperties properties;
    private final InstagramGraphClient graphClient;
    private final InstagramAccountRepository accountRepository;
    private final InstagramMediaRepository mediaRepository;
    private final InstagramMediaChildRepository childRepository;
    private final SyncJobRepository syncJobRepository;
    private final InstagramAccountService accountService;

    public InstagramSyncService(
            InstagramProperties properties,
            InstagramGraphClient graphClient,
            InstagramAccountRepository accountRepository,
            InstagramMediaRepository mediaRepository,
            InstagramMediaChildRepository childRepository,
            SyncJobRepository syncJobRepository,
            InstagramAccountService accountService
    ) {
        this.properties = properties;
        this.graphClient = graphClient;
        this.accountRepository = accountRepository;
        this.mediaRepository = mediaRepository;
        this.childRepository = childRepository;
        this.syncJobRepository = syncJobRepository;
        this.accountService = accountService;
    }

    @Transactional
    public SyncJob enqueueHistoricalSync(InstagramAccount account) {
        var running = syncJobRepository.findFirstByInstagramAccountIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                account.getId(),
                SyncJobType.HISTORICAL,
                List.of(SyncJobStatus.PENDING, SyncJobStatus.RUNNING)
        );
        if (running.isPresent()) {
            return running.get();
        }
        SyncJob job = new SyncJob();
        job.setTenantId(account.getTenantId());
        job.setWorkspaceId(account.getWorkspaceId());
        job.setInstagramAccountId(account.getId());
        job.setJobType(SyncJobType.HISTORICAL);
        job.setStatus(SyncJobStatus.PENDING);
        return syncJobRepository.save(job);
    }

    @Transactional
    public SyncJobResponse triggerSync(UUID accountId, CustomUserDetails principal) {
        InstagramAccount account = accountService.requireAccountForUser(accountId, principal);
        if (account.getStatus() == InstagramAccountStatus.NEEDS_REAUTH) {
            throw new ApiException(HttpStatus.CONFLICT, MessageKeys.INSTAGRAM_NEEDS_REAUTH);
        }
        SyncJob pending = enqueueHistoricalSync(account);
        SyncJob job = executeJob(pending, account, true);
        return toResponse(job);
    }

    @Transactional
    public void processPendingJobs() {
        if (!properties.isEnabled()) {
            return;
        }
        List<SyncJob> pending = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING).stream()
                .limit(5)
                .toList();
        for (SyncJob job : pending) {
            accountRepository.findById(job.getInstagramAccountId()).ifPresent(account -> {
                try {
                    executeJob(job, account, job.getJobType() == SyncJobType.HISTORICAL);
                } catch (Exception e) {
                    log.warn("Pending sync failed for job {}: {}", job.getId(), e.getMessage());
                }
            });
        }
    }

    @Transactional
    public void runIncrementalForActiveAccounts() {
        if (!properties.isEnabled()) {
            return;
        }
        for (InstagramAccount account : accountRepository.findByStatus(InstagramAccountStatus.ACTIVE)) {
            try {
                SyncJob job = new SyncJob();
                job.setTenantId(account.getTenantId());
                job.setWorkspaceId(account.getWorkspaceId());
                job.setInstagramAccountId(account.getId());
                job.setJobType(SyncJobType.INCREMENTAL);
                job.setStatus(SyncJobStatus.PENDING);
                job = syncJobRepository.save(job);
                executeJob(job, account, false);
            } catch (Exception e) {
                log.warn("Incremental sync failed for account {}: {}", account.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void refreshExpiringTokens() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant threshold = Instant.now().plusSeconds(properties.getTokenRefreshLeadDays() * 24 * 3600);
        List<InstagramAccount> due = accountRepository
                .findByStatusAndTokenExpiresAtBefore(InstagramAccountStatus.ACTIVE, threshold);
        for (InstagramAccount account : due) {
            try {
                var refreshed = graphClient.refreshLongLivedToken(account.getAccessToken());
                account.setAccessToken(refreshed.accessToken());
                if (refreshed.expiresInSeconds() != null) {
                    account.setTokenExpiresAt(Instant.now().plusSeconds(refreshed.expiresInSeconds()));
                }
                accountRepository.save(account);
            } catch (Exception e) {
                log.warn("Token refresh failed for account {}: {}", account.getId(), e.getMessage());
                account.setStatus(InstagramAccountStatus.NEEDS_REAUTH);
                accountRepository.save(account);
            }
        }
    }

    private SyncJob executeJob(SyncJob job, InstagramAccount account, boolean fullHistory) {
        job.setStatus(SyncJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job = syncJobRepository.save(job);

        try {
            int pages = 0;
            int upserted = 0;
            String after = null;
            int maxPages = fullHistory ? properties.getHistoricalMaxPages() : 2;

            while (pages < maxPages) {
                InstagramGraphClient.MediaPage page = graphClient.fetchMediaPage(
                        account.getIgUserId(), account.getAccessToken(), after);
                if (page.data().isEmpty()) {
                    break;
                }
                int knownHits = 0;
                for (Map<String, Object> item : page.data()) {
                    String igMediaId = stringVal(item.get("id"));
                    if (igMediaId == null) {
                        continue;
                    }
                    boolean existed = mediaRepository.existsByTenantIdAndIgMediaId(account.getTenantId(), igMediaId);
                    upsertMedia(account, item);
                    upserted++;
                    if (!fullHistory && existed) {
                        knownHits++;
                    }
                }
                pages++;
                job.setPagesFetched(pages);
                job.setItemsUpserted(upserted);
                job.setCursorAfter(page.nextAfter());
                syncJobRepository.save(job);

                if (!fullHistory && knownHits == page.data().size()) {
                    break;
                }
                if (page.nextAfter() == null || page.nextAfter().isBlank()) {
                    break;
                }
                after = page.nextAfter();
            }

            account.setLastSyncedAt(Instant.now());
            accountRepository.save(account);

            job.setStatus(SyncJobStatus.SUCCESS);
            job.setFinishedAt(Instant.now());
            return syncJobRepository.save(job);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Sync failed for account {}", account.getId(), e);
            if (isAuthError(e)) {
                account.setStatus(InstagramAccountStatus.NEEDS_REAUTH);
                accountRepository.save(account);
            }
            job.setStatus(SyncJobStatus.FAILED);
            job.setErrorMessage(trim(e.getMessage(), 1000));
            job.setFinishedAt(Instant.now());
            syncJobRepository.save(job);
            throw new ApiException(HttpStatus.BAD_GATEWAY, MessageKeys.SERVER_INTERNAL);
        }
    }

    private void upsertMedia(InstagramAccount account, Map<String, Object> item) {
        String igMediaId = stringVal(item.get("id"));
        InstagramMedia media = mediaRepository.findByTenantIdAndIgMediaId(account.getTenantId(), igMediaId)
                .orElseGet(InstagramMedia::new);
        boolean isNew = media.getId() == null;
        media.setTenantId(account.getTenantId());
        media.setWorkspaceId(account.getWorkspaceId());
        media.setInstagramAccountId(account.getId());
        media.setIgMediaId(igMediaId);
        media.setMediaType(parseType(stringVal(item.get("media_type"))));
        media.setCaption(stringVal(item.get("caption")));
        media.setMediaUrl(stringVal(item.get("media_url")));
        media.setThumbnailUrl(stringVal(item.get("thumbnail_url")));
        media.setPermalink(stringVal(item.get("permalink")));
        media.setPublishedAt(parseInstant(stringVal(item.get("timestamp"))));
        media.setMediaUrlFetchedAt(Instant.now());
        media.setRawPayload(item);
        if (isNew) {
            media.setAnalysisStatus(MediaAnalysisStatus.NOT_ANALYZED);
        }
        media = mediaRepository.save(media);

        if (media.getMediaType() == InstagramMediaType.CAROUSEL_ALBUM) {
            List<Map<String, Object>> children = graphClient.fetchCarouselChildren(igMediaId, account.getAccessToken());
            int pos = 0;
            for (Map<String, Object> child : children) {
                String childId = stringVal(child.get("id"));
                if (childId == null) {
                    continue;
                }
                InstagramMediaChild entity = childRepository
                        .findByParentMediaIdAndIgMediaId(media.getId(), childId)
                        .orElseGet(InstagramMediaChild::new);
                entity.setTenantId(account.getTenantId());
                entity.setParentMediaId(media.getId());
                entity.setIgMediaId(childId);
                entity.setMediaType(parseType(stringVal(child.get("media_type"))));
                entity.setMediaUrl(stringVal(child.get("media_url")));
                entity.setThumbnailUrl(stringVal(child.get("thumbnail_url")));
                entity.setPosition(pos++);
                childRepository.save(entity);
            }
        }
    }

    private static InstagramMediaType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return InstagramMediaType.UNKNOWN;
        }
        try {
            return InstagramMediaType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return InstagramMediaType.UNKNOWN;
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean isAuthError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("oauth") || msg.contains("session has expired") || msg.contains("invalid oauth");
    }

    private SyncJobResponse toResponse(SyncJob job) {
        return new SyncJobResponse(
                job.getId(),
                job.getInstagramAccountId(),
                job.getJobType(),
                job.getStatus(),
                job.getPagesFetched(),
                job.getItemsUpserted(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
