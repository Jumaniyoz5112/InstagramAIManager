package uzeduai.be.instagram.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InstagramGraphClient {

    TokenExchangeResult exchangeCode(String code);

    TokenExchangeResult refreshLongLivedToken(String currentAccessToken);

    IgUserProfile fetchMe(String accessToken);

    /**
     * Discover all Instagram Business/Creator accounts reachable with the token (deduped by id).
     * Empty list means discovery failed for this token (caller maps to user-facing error).
     */
    List<IgUserProfile> discoverInstagramAccounts(String accessToken);

    MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor);

    List<Map<String, Object>> fetchCarouselChildren(String igMediaId, String accessToken);

    Optional<Map<String, Object>> fetchMediaFields(String igMediaId, String accessToken, String fields);

    /**
     * Media insights for shares/saved. Empty map on permission/API errors (sync must continue).
     * Implementations may throw {@link InsightsPermissionException} when the token lacks insights access
     * so callers can skip remaining insight calls for the job.
     */
    Map<String, Long> fetchMediaInsights(String igMediaId, String accessToken);

    /** Thrown when Graph rejects insights due to missing app/token permission (#10). */
    class InsightsPermissionException extends RuntimeException {
        public InsightsPermissionException(String message) {
            super(message);
        }
    }

    /**
     * Top-level comments with one-level replies nested under each item as {@code replies}.
     */
    List<Map<String, Object>> fetchMediaComments(String igMediaId, String accessToken);

    /**
     * Create an IG media container (image / reel / story / carousel child or parent).
     * {@code params} are Graph form fields (image_url, video_url, media_type, caption, …).
     *
     * @return container id
     */
    String createMediaContainer(String igUserId, String accessToken, Map<String, String> params);

    /** Poll container processing status ({@code FINISHED}, {@code IN_PROGRESS}, {@code ERROR}, …). */
    ContainerStatus fetchContainerStatus(String containerId, String accessToken);

    /** Publish a finished container; returns published IG media id. */
    String publishMedia(String igUserId, String accessToken, String creationId);

    /** Rolling 24h content publishing quota. */
    PublishingLimit fetchContentPublishingLimit(String igUserId, String accessToken);

    record TokenExchangeResult(String accessToken, Long expiresInSeconds) {
    }

    record ContainerStatus(String statusCode, String status) {
    }

    record PublishingLimit(int quotaTotal, int quotaUsage) {
    }

    /**
     * @param accessToken optional Page access token for Unified/Facebook Login path;
     *                    null means keep the user token from code exchange.
     */
    record IgUserProfile(String id, String username, String accountType, String accessToken) {
        public IgUserProfile(String id, String username, String accountType) {
            this(id, username, accountType, null);
        }
    }

    record MediaPage(List<Map<String, Object>> data, String nextAfter) {
    }
}
