package uzeduai.be.analysis;

import org.junit.jupiter.api.Test;
import uzeduai.be.analysis.service.ContentInsightsService;
import uzeduai.be.instagram.entity.InstagramMedia;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentInsightsEngagementScoreTest {

    @Test
    void engagementScoreWeightsCommentsSharesAndSaves() {
        InstagramMedia media = new InstagramMedia();
        media.setLikeCount(10);
        media.setCommentsCount(4);
        media.setSharesCount(3);
        media.setSavesCount(2);

        // likes + 3*comments + 2*shares + 2*saves = 10 + 12 + 6 + 4 = 32
        assertEquals(32.0, ContentInsightsService.engagementScore(media), 0.001);
    }

    @Test
    void engagementScoreTreatsNullMetricsAsZero() {
        InstagramMedia media = new InstagramMedia();
        assertEquals(0.0, ContentInsightsService.engagementScore(media), 0.001);
    }
}
