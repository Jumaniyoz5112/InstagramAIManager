package uzeduai.be.instagram.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.instagram.enabled", havingValue = "true")
public class InstagramScheduler {

    private final InstagramSyncService syncService;

    public InstagramScheduler(InstagramSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${app.instagram.pending-sync-interval-ms:30000}")
    public void processPending() {
        syncService.processPendingJobs();
    }

    @Scheduled(fixedDelayString = "${app.instagram.incremental-sync-interval-ms:900000}")
    public void incrementalSync() {
        syncService.runIncrementalForActiveAccounts();
    }

    @Scheduled(cron = "${app.instagram.token-refresh-cron:0 30 3 * * *}")
    public void refreshTokens() {
        syncService.refreshExpiringTokens();
    }
}
