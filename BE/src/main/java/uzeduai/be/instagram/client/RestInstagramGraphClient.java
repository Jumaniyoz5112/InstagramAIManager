package uzeduai.be.instagram.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uzeduai.be.instagram.config.InstagramProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.instagram.enabled", havingValue = "true")
public class RestInstagramGraphClient implements InstagramGraphClient {

    private static final String MEDIA_FIELDS =
            "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final InstagramProperties properties;

    public RestInstagramGraphClient(InstagramProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public TokenExchangeResult exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getAppId());
        form.add("client_secret", properties.getAppSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code", code);

        Map<String, Object> shortLived = restClient.post()
                .uri("https://api.instagram.com/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);

        return exchangeLongLived(stringVal(shortLived, "access_token"));
    }

    @Override
    public TokenExchangeResult refreshLongLivedToken(String currentAccessToken) {
        String url = UriComponentsBuilder
                .fromUriString(properties.getApiBaseUrl() + "/refresh_access_token")
                .queryParam("grant_type", "ig_refresh_token")
                .queryParam("access_token", currentAccessToken)
                .toUriString();
        Map<String, Object> node = restClient.get().uri(url).retrieve().body(MAP_TYPE);
        return new TokenExchangeResult(stringVal(node, "access_token"), longVal(node, "expires_in"));
    }

    @Override
    public IgUserProfile fetchMe(String accessToken) {
        String url = UriComponentsBuilder
                .fromUriString(api("/me"))
                .queryParam("fields", "user_id,username,account_type")
                .queryParam("access_token", accessToken)
                .toUriString();
        Map<String, Object> node = restClient.get().uri(url).retrieve().body(MAP_TYPE);
        String id = firstNonBlank(stringVal(node, "user_id"), stringVal(node, "id"));
        return new IgUserProfile(id, stringVal(node, "username"), stringVal(node, "account_type"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(api("/" + igUserId + "/media"))
                .queryParam("fields", MEDIA_FIELDS)
                .queryParam("access_token", accessToken)
                .queryParam("limit", 25);
        if (afterCursor != null && !afterCursor.isBlank()) {
            builder.queryParam("after", afterCursor);
        }
        Map<String, Object> node = restClient.get().uri(builder.toUriString()).retrieve().body(MAP_TYPE);
        List<Map<String, Object>> data = new ArrayList<>();
        Object rawData = node == null ? null : node.get("data");
        if (rawData instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    data.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        String nextAfter = null;
        Object paging = node == null ? null : node.get("paging");
        if (paging instanceof Map<?, ?> pagingMap) {
            Object cursors = pagingMap.get("cursors");
            if (cursors instanceof Map<?, ?> cursorsMap && cursorsMap.get("after") != null) {
                nextAfter = cursorsMap.get("after").toString();
            }
        }
        return new MediaPage(data, nextAfter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchCarouselChildren(String igMediaId, String accessToken) {
        String url = UriComponentsBuilder
                .fromUriString(api("/" + igMediaId + "/children"))
                .queryParam("fields", "id,media_type,media_url,thumbnail_url,timestamp")
                .queryParam("access_token", accessToken)
                .toUriString();
        Map<String, Object> node = restClient.get().uri(url).retrieve().body(MAP_TYPE);
        List<Map<String, Object>> data = new ArrayList<>();
        Object rawData = node == null ? null : node.get("data");
        if (rawData instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    data.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return data;
    }

    @Override
    public Optional<Map<String, Object>> fetchMediaFields(String igMediaId, String accessToken, String fields) {
        String url = UriComponentsBuilder
                .fromUriString(api("/" + igMediaId))
                .queryParam("fields", fields)
                .queryParam("access_token", accessToken)
                .toUriString();
        try {
            Map<String, Object> node = restClient.get().uri(url).retrieve().body(MAP_TYPE);
            if (node == null || node.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(node);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private TokenExchangeResult exchangeLongLived(String shortLivedToken) {
        String url = UriComponentsBuilder
                .fromUriString(properties.getApiBaseUrl() + "/access_token")
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("access_token", shortLivedToken)
                .toUriString();
        Map<String, Object> node = restClient.get().uri(url).retrieve().body(MAP_TYPE);
        return new TokenExchangeResult(stringVal(node, "access_token"), longVal(node, "expires_in"));
    }

    private String api(String path) {
        String base = properties.getApiBaseUrl();
        String version = properties.getGraphVersion();
        if (path.startsWith("/")) {
            return base + "/" + version + path;
        }
        return base + "/" + version + "/" + path;
    }

    private static String stringVal(Map<String, Object> node, String field) {
        if (node == null || node.get(field) == null) {
            return null;
        }
        return node.get(field).toString();
    }

    private static Long longVal(Map<String, Object> node, String field) {
        if (node == null || node.get(field) == null) {
            return null;
        }
        Object value = node.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
