package uzeduai.be.instagram.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InstagramGraphClient {

    TokenExchangeResult exchangeCode(String code);

    TokenExchangeResult refreshLongLivedToken(String currentAccessToken);

    IgUserProfile fetchMe(String accessToken);

    MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor);

    List<Map<String, Object>> fetchCarouselChildren(String igMediaId, String accessToken);

    Optional<Map<String, Object>> fetchMediaFields(String igMediaId, String accessToken, String fields);

    record TokenExchangeResult(String accessToken, Long expiresInSeconds) {
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
