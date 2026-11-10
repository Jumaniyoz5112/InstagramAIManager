package uzeduai.be.instagram.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uzeduai.be.instagram.config.InstagramProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.instagram.enabled", havingValue = "true")
public class RestInstagramGraphClient implements InstagramGraphClient {

    private static final Logger log = LoggerFactory.getLogger(RestInstagramGraphClient.class);

    private static final String MEDIA_FIELDS =
            "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final InstagramProperties properties;

    public RestInstagramGraphClient(InstagramProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        JsonMapper jsonMapper = objectMapper instanceof JsonMapper jm ? jm : JsonMapper.builder().build();
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter(jsonMapper);
        // Meta Graph / OAuth occasionally returns JSON as text/javascript
        jsonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"),
                new MediaType("text", "javascript"),
                new MediaType("application", "javascript"),
                MediaType.TEXT_PLAIN
        ));
        this.restClient = RestClient.builder()
                .configureMessageConverters(client -> client
                        .registerDefaults()
                        .withJsonConverter(jsonConverter))
                .build();
    }

    @Override
    public TokenExchangeResult exchangeCode(String code) {
        if (properties.isUnifiedLogin()) {
            return exchangeUnifiedCode(code);
        }
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
        if (properties.isUnifiedLogin()) {
            // System-user / business tokens from Unified Login typically do not use IG refresh endpoint.
            return new TokenExchangeResult(currentAccessToken, null);
        }
        String url = UriComponentsBuilder
                .fromUriString(properties.getApiBaseUrl() + "/refresh_access_token")
                .queryParam("grant_type", "ig_refresh_token")
                .queryParam("access_token", currentAccessToken)
                .toUriString();
        Map<String, Object> node = getMap(url);
        return new TokenExchangeResult(stringVal(node, "access_token"), longVal(node, "expires_in"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public IgUserProfile fetchMe(String accessToken) {
        if (properties.isUnifiedLogin()) {
            // Instagram Login Compatible: graph.instagram.com /me after IG code exchange.
            Optional<IgUserProfile> fromIgGraph = safe(() -> fetchIgFromInstagramGraphMe(accessToken));
            if (fromIgGraph.isPresent()) {
                return fromIgGraph.get();
            }
            // Business Integration System User token path (Facebook Graph).
            Optional<IgUserProfile> fromAssigned = safe(() ->
                    fetchFirstIgFromListBearer(fbApi("/me/assigned_instagram_accounts"), accessToken, "id,username"));
            if (fromAssigned.isPresent()) {
                return fromAssigned.get();
            }
            // Retry with query-param style for older Graph hosts.
            Optional<IgUserProfile> fromAssignedQuery = safe(() ->
                    fetchFirstIgFromList(fbApi("/me/assigned_instagram_accounts"), accessToken, "id,username"));
            if (fromAssignedQuery.isPresent()) {
                return fromAssignedQuery.get();
            }
            Optional<IgUserProfile> fromDebug = safe(() -> fetchIgFromDebugToken(accessToken));
            if (fromDebug.isPresent()) {
                return fromDebug.get();
            }
            Optional<IgUserProfile> fromClientBusiness = safe(() -> fetchIgFromClientBusiness(accessToken));
            if (fromClientBusiness.isPresent()) {
                return fromClientBusiness.get();
            }
            Optional<IgUserProfile> fromPages = safe(() -> fetchIgFromFacebookPages(accessToken));
            if (fromPages.isPresent()) {
                return fromPages.get();
            }
            Optional<IgUserProfile> fromBusiness = safe(() -> fetchIgFromBusinesses(accessToken));
            if (fromBusiness.isPresent()) {
                return fromBusiness.get();
            }
            throw new IllegalStateException(
                    "No Instagram accounts found for Unified Login token. "
                            + "Confirm a professional IG account was shared in the Login dialog, "
                            + "and that the Login Configuration requests Instagram assets."
            );
        }
        return fetchIgFromInstagramGraphMe(accessToken)
                .orElseThrow(() -> new IllegalStateException("Instagram /me returned empty profile"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor) {
        // Facebook Login / System User tokens must hit graph.facebook.com (not graph.instagram.com).
        // Prefer Bearer header so long tokens are not mangled in query strings.
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(fbApi("/" + igUserId + "/media"))
                .queryParam("fields", MEDIA_FIELDS)
                .queryParam("limit", 25);
        if (afterCursor != null && !afterCursor.isBlank()) {
            builder.queryParam("after", afterCursor);
        }
        Map<String, Object> node = getMapWithBearer(builder.toUriString(), accessToken);
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
                .fromUriString(fbApi("/" + igMediaId + "/children"))
                .queryParam("fields", "id,media_type,media_url,thumbnail_url,timestamp")
                .toUriString();
        Map<String, Object> node = getMapWithBearer(url, accessToken);
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
            Map<String, Object> node = getMap(url);
            if (node == null || node.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(node);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private TokenExchangeResult exchangeUnifiedCode(String code) {
        // Instagram Login Compatible configs: prefer graph.instagram.com code exchange.
        // Facebook Graph exchange often yields a User token without usable IG target_ids.
        try {
            String igUrl = UriComponentsBuilder
                    .fromUriString(igApi("/oauth/access_token"))
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", properties.getAppId())
                    .queryParam("client_secret", properties.getAppSecret())
                    .queryParam("redirect_uri", properties.getRedirectUri())
                    .queryParam("code", code)
                    .toUriString();
            Map<String, Object> igNode = getMap(igUrl);
            String igToken = stringVal(igNode, "access_token");
            if (igToken != null && !igToken.isBlank()) {
                log.warn("Unified Login exchanged code via graph.instagram.com (user_id={})",
                        firstNonBlank(stringVal(igNode, "user_id"), stringVal(igNode, "id")));
                Long expires = longVal(igNode, "expires_in");
                if (expires == null) {
                    // Short-lived IG tokens still need the long-lived exchange when possible.
                    try {
                        return exchangeLongLived(igToken);
                    } catch (Exception refreshEx) {
                        log.warn("IG long-lived exchange skipped: {}", refreshEx.getMessage());
                        return new TokenExchangeResult(igToken, null);
                    }
                }
                return new TokenExchangeResult(igToken, expires);
            }
        } catch (Exception igEx) {
            log.warn("graph.instagram.com code exchange failed, falling back to Facebook Graph: {}",
                    igEx.getMessage());
        }

        String url = UriComponentsBuilder
                .fromUriString(fbApi("/oauth/access_token"))
                .queryParam("client_id", properties.getAppId())
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("code", code)
                .toUriString();
        Map<String, Object> node = getMap(url);
        return new TokenExchangeResult(stringVal(node, "access_token"), longVal(node, "expires_in"));
    }

    private TokenExchangeResult exchangeLongLived(String shortLivedToken) {
        String url = UriComponentsBuilder
                .fromUriString(properties.getApiBaseUrl() + "/access_token")
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("access_token", shortLivedToken)
                .toUriString();
        Map<String, Object> node = getMap(url);
        return new TokenExchangeResult(stringVal(node, "access_token"), longVal(node, "expires_in"));
    }

    private Map<String, Object> getMap(String url) {
        // URI.create avoids RestClient treating {…} Graph field expanders as URI templates
        // and double-encoding query values.
        return restClient.get().uri(URI.create(url)).retrieve().body(MAP_TYPE);
    }

    /** Unified Login SUAT discovery prefers Authorization Bearer (Meta docs / Adeli). */
    private Map<String, Object> getMapWithBearer(String urlWithoutToken, String accessToken) {
        return restClient.get()
                .uri(URI.create(urlWithoutToken))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MAP_TYPE);
    }

    private Optional<IgUserProfile> safe(java.util.function.Supplier<Optional<IgUserProfile>> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            log.warn("Instagram account discovery step failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchIgFromDebugToken(String userAccessToken) {
        String appToken = properties.getAppId() + "|" + properties.getAppSecret();
        String url = fbApi("/debug_token")
                + "?input_token=" + URLEncoder.encode(userAccessToken, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(appToken, StandardCharsets.UTF_8);
        Map<String, Object> root = getMap(url);
        Object dataObj = root == null ? null : root.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return Optional.empty();
        }
        log.warn("Unified Login debug_token type={} app_id={} user_id={} is_valid={} scopes={} granular_scopes={}",
                data.get("type"), data.get("app_id"), data.get("user_id"), data.get("is_valid"),
                data.get("scopes"), data.get("granular_scopes"));
        Object granular = data.get("granular_scopes");
        if (!(granular instanceof List<?> scopes)) {
            return Optional.empty();
        }
        for (Object scopeItem : scopes) {
            if (!(scopeItem instanceof Map<?, ?> scopeMap)) {
                continue;
            }
            String scope = scopeMap.get("scope") == null ? "" : scopeMap.get("scope").toString();
            Object targets = scopeMap.get("target_ids");
            if (!(targets instanceof List<?> ids) || ids.isEmpty()) {
                continue;
            }
            // Prefer Instagram-named scopes; otherwise probe target ids for username field.
            boolean likelyIg = scope.toLowerCase().contains("instagram");
            for (Object rawId : ids) {
                if (rawId == null) {
                    continue;
                }
                String candidateId = rawId.toString();
                if (candidateId.isBlank() || candidateId.startsWith("act_")) {
                    continue;
                }
                Optional<IgUserProfile> profile = fetchIgProfile(candidateId, userAccessToken);
                if (profile.isPresent()) {
                    return profile;
                }
                if (likelyIg) {
                    return Optional.of(new IgUserProfile(candidateId, null, "BUSINESS"));
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchIgFromClientBusiness(String accessToken) {
        String meUrl = fbApi("/me")
                + "?fields=" + URLEncoder.encode("id,name,client_business_id", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        Map<String, Object> me = getMap(meUrl);
        String clientBusinessId = stringVal(me, "client_business_id");
        log.warn("Unified Login /me id={} client_business_id={}", stringVal(me, "id"), clientBusinessId);
        if (clientBusinessId == null || clientBusinessId.isBlank()) {
            return Optional.empty();
        }
        for (String edge : List.of(
                "owned_instagram_accounts",
                "client_instagram_accounts",
                "instagram_accounts",
                "instagram_business_accounts"
        )) {
            Optional<IgUserProfile> found = fetchFirstIgFromList(
                    fbApi("/" + clientBusinessId + "/" + edge),
                    accessToken,
                    "id,username"
            );
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<IgUserProfile> fetchIgFromInstagramGraphMe(String accessToken) {
        for (String fields : List.of("user_id,username,account_type", "id,username,account_type")) {
            String url = UriComponentsBuilder
                    .fromUriString(igApi("/me"))
                    .queryParam("fields", fields)
                    .queryParam("access_token", accessToken)
                    .toUriString();
            Map<String, Object> node = getMap(url);
            String id = firstNonBlank(stringVal(node, "user_id"), stringVal(node, "id"));
            if (id != null && !id.isBlank()) {
                return Optional.of(new IgUserProfile(
                        id,
                        stringVal(node, "username"),
                        stringVal(node, "account_type")
                ));
            }
        }
        return Optional.empty();
    }

    private Optional<IgUserProfile> fetchIgProfile(String igId, String accessToken) {
        for (String base : List.of(igApi("/" + igId), fbApi("/" + igId))) {
            try {
                String profileUrl = base
                        + "?fields=" + URLEncoder.encode("id,username", StandardCharsets.UTF_8)
                        + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
                Map<String, Object> profile = getMap(profileUrl);
                String username = stringVal(profile, "username");
                String id = firstNonBlank(stringVal(profile, "id"), igId);
                if (username != null && !username.isBlank()) {
                    return Optional.of(new IgUserProfile(id, username, "BUSINESS"));
                }
            } catch (Exception ignored) {
                // try next host
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchIgFromFacebookPages(String accessToken) {
        // Build manually: UriComponentsBuilder treats `{id,username}` as a URI template.
        String url = fbApi("/me/accounts")
                + "?fields=" + URLEncoder.encode(
                "id,name,access_token,instagram_business_account{id,username}", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        Map<String, Object> node = getMap(url);
        Object rawData = node == null ? null : node.get("data");
        if (!(rawData instanceof List<?> list)) {
            return Optional.empty();
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> page)) {
                continue;
            }
            Object igNode = page.get("instagram_business_account");
            if (igNode instanceof Map<?, ?> ig) {
                Map<String, Object> account = new LinkedHashMap<>((Map<String, Object>) ig);
                String id = stringVal(account, "id");
                if (id != null && !id.isBlank()) {
                    String pageToken = page.get("access_token") == null
                            ? null
                            : page.get("access_token").toString();
                    return Optional.of(new IgUserProfile(
                            id,
                            stringVal(account, "username"),
                            "BUSINESS",
                            pageToken
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchIgFromBusinesses(String accessToken) {
        String businessesUrl = UriComponentsBuilder
                .fromUriString(fbApi("/me/businesses"))
                .queryParam("fields", "id,name")
                .queryParam("access_token", accessToken)
                .toUriString();
        Map<String, Object> businesses = getMap(businessesUrl);
        Object raw = businesses == null ? null : businesses.get("data");
        if (!(raw instanceof List<?> list)) {
            return Optional.empty();
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> biz)) {
                continue;
            }
            String businessId = biz.get("id") == null ? null : biz.get("id").toString();
            if (businessId == null || businessId.isBlank()) {
                continue;
            }
            for (String edge : List.of("owned_instagram_accounts", "client_instagram_accounts", "instagram_accounts")) {
                Optional<IgUserProfile> found = fetchFirstIgFromList(
                        fbApi("/" + businessId + "/" + edge),
                        accessToken,
                        "id,username"
                );
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchFirstIgFromList(String path, String accessToken, String fields) {
        String url = path
                + (path.contains("?") ? "&" : "?")
                + "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        return parseIgList(path, getMap(url));
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> fetchFirstIgFromListBearer(String path, String accessToken, String fields) {
        String url = path
                + (path.contains("?") ? "&" : "?")
                + "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8);
        return parseIgList(path + " (bearer)", getMapWithBearer(url, accessToken));
    }

    @SuppressWarnings("unchecked")
    private Optional<IgUserProfile> parseIgList(String pathLabel, Map<String, Object> node) {
        Object rawData = node == null ? null : node.get("data");
        if (!(rawData instanceof List<?> list) || list.isEmpty()) {
            log.warn("Unified Login list empty for {}", pathLabel);
            return Optional.empty();
        }
        if (list.getFirst() instanceof Map<?, ?> first) {
            Map<String, Object> account = new LinkedHashMap<>((Map<String, Object>) first);
            String id = stringVal(account, "id");
            if (id != null && !id.isBlank()) {
                return Optional.of(new IgUserProfile(id, stringVal(account, "username"), "BUSINESS"));
            }
        }
        return Optional.empty();
    }

    private String api(String path) {
        // Instagram Login Compatible tokens (graph.instagram.com exchange) use IG Graph.
        // Facebook BISU / Page-token discovery helpers call fbApi() explicitly.
        return igApi(path);
    }

    private String igApi(String path) {
        String base = properties.getApiBaseUrl();
        String version = properties.getGraphVersion();
        if (path.startsWith("/")) {
            return base + "/" + version + path;
        }
        return base + "/" + version + "/" + path;
    }

    private String fbApi(String path) {
        String base = properties.getFacebookGraphBaseUrl();
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
