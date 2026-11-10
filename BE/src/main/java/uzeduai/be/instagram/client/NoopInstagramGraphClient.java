package uzeduai.be.instagram.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Active when Instagram integration is disabled (local default).
 * Prevents accidental live Meta calls without credentials.
 */
@Component
@ConditionalOnProperty(name = "app.instagram.enabled", havingValue = "false", matchIfMissing = true)
public class NoopInstagramGraphClient implements InstagramGraphClient {

    @Override
    public TokenExchangeResult exchangeCode(String code) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public TokenExchangeResult refreshLongLivedToken(String currentAccessToken) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public IgUserProfile fetchMe(String accessToken) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public List<Map<String, Object>> fetchCarouselChildren(String igMediaId, String accessToken) {
        return List.of();
    }

    @Override
    public Optional<Map<String, Object>> fetchMediaFields(String igMediaId, String accessToken, String fields) {
        return Optional.empty();
    }

    @Override
    public Map<String, Long> fetchMediaInsights(String igMediaId, String accessToken) {
        return Map.of();
    }

    @Override
    public List<Map<String, Object>> fetchMediaComments(String igMediaId, String accessToken) {
        return List.of();
    }

    @Override
    public String createMediaContainer(String igUserId, String accessToken, Map<String, String> params) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public ContainerStatus fetchContainerStatus(String containerId, String accessToken) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public String publishMedia(String igUserId, String accessToken, String creationId) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }

    @Override
    public PublishingLimit fetchContentPublishingLimit(String igUserId, String accessToken) {
        throw new UnsupportedOperationException("Instagram integration is disabled (app.instagram.enabled=false)");
    }
}
