package uzeduai.be.analysis.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uzeduai.be.analysis.config.AiServiceProperties;

@Component
@ConditionalOnProperty(name = "app.ai-service.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisScheduler {

    private final ContentAnalysisService contentAnalysisService;
    private final EngagementSnapshotService engagementSnapshotService;
    private final AiServiceProperties properties;

    public AnalysisScheduler(
            ContentAnalysisService contentAnalysisService,
            EngagementSnapshotService engagementSnapshotService,
            AiServiceProperties properties
    ) {
        this.contentAnalysisService = contentAnalysisService;
        this.engagementSnapshotService = engagementSnapshotService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ai-service.queue-poll-interval-ms:30000}")
    public void processContentAnalysisQueue() {
        if (!properties.isEnabled()) {
            return;
        }
        contentAnalysisService.processQueuedJobs();
    }

    @Scheduled(cron = "${app.ai-service.engagement-cron:0 0 8,20 * * *}", zone = "${app.ai-service.engagement-zone:Asia/Tashkent}")
    public void morningEveningEngagement() {
        if (!properties.isEnabled()) {
            return;
        }
        engagementSnapshotService.captureScheduledSlot();
    }
}
