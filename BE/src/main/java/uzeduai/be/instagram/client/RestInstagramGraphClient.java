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
            "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp,"
                    + "like_count,comments_count,shares_count,saved_count";
    private static final String COMMENT_FIELDS =
            "id,text,username,timestamp,like_count,from{id,username},"
                    + "replies{id,text,username,timestamp,like_count,from{id,username}}";
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
            try {
                return exchangeFacebookLongLived(currentAccessToken);
            } catch (Exception ex) {
                log.warn("Unified FB token refresh skipped: {}", ex.getMessage());
                return new TokenExchangeResult(currentAccessToken, null);
            }
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
    public IgUserProfile fetchMe(String accessToken) {
        List<IgUserProfile> accounts = discoverInstagramAccounts(accessToken);
        if (accounts.isEmpty()) {
            throw new IllegalStateException(
                    "No Instagram accounts found for token. "
                            + "Confirm a professional IG account was shared in the Login dialog, "
                            + "Page is linked, and Login Configuration requests Instagram (+ Page) assets."
            );
        }
        return accounts.getFirst();
    }

    @Override
    public List<IgUserProfile> discoverInstagramAccounts(String accessToken) {
        LinkedHashMap<String, IgUserProfile> byId = new LinkedHashMap<>();
        if (properties.isUnifiedLogin()) {
            // Prefer real end-user paths first; assigned_instagram_accounts is often empty for portfolio users.
            addAll(byId, safeList(() -> listFromInstagramGraphMe(accessToken)));
            addAll(byId, safeList(() -> listFromFacebookPages(accessToken)));
            addAll(byId, safeList(() -> listFromBusinesses(accessToken)));
            addAll(byId, safeList(() -> listFromClientBusiness(accessToken)));
            addAll(byId, safeList(() -> listFromDebugToken(accessToken)));
            addAll(byId, safeList(() ->
                    listIgFromListBearer(fbApi("/me/assigned_instagram_accounts"), accessToken, "id,username")));
            addAll(byId, safeList(() ->
                    listIgFromList(fbApi("/me/assigned_instagram_accounts"), accessToken, "id,username")));
        } else {
            addAll(byId, safeList(() -> listFromInstagramGraphMe(accessToken)));
        }
        return List.copyOf(byId.values());
    }

    @Override
    @SuppressWarnings("unchecked")
    public MediaPage fetchMediaPage(String igUserId, String accessToken, String afterCursor) {
        // Facebook Login / System User tokens must hit graph.facebook.com (not graph.instagram.com).
        // Prefer Bearer header so long tokens are not mangled in query strings.
        try {
            return fetchMediaPageWithFields(igUserId, accessToken, afterCursor, MEDIA_FIELDS);
        } catch (Exception fullFieldsError) {
            log.warn("Media page with metrics fields failed, retrying core fields: {}", fullFieldsError.getMessage());
            return fetchMediaPageWithFields(
                    igUserId,
                    accessToken,
                    afterCursor,
                    "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp,like_count,comments_count"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private MediaPage fetchMediaPageWithFields(
            String igUserId,
            String accessToken,
            String afterCursor,
            String fields
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(fbApi("/" + igUserId + "/media"))
                .queryParam("fields", fields)
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

    @Override
    public Map<String, Long> fetchMediaInsights(String igMediaId, String accessToken) {
        Map<String, Long> result = new LinkedHashMap<>();
        // Prefer a single batch first; fall back to one-by-one if Meta rejects the combo.
        try {
            return parseInsights(getMapWithBearer(insightsUrl(igMediaId, "shares,saved"), accessToken));
        } catch (Exception batchError) {
            if (isPermissionDenied(batchError)) {
                throw new InsightsPermissionException(batchError.getMessage());
            }
            log.debug("Batch insights failed for {}: {}", igMediaId, batchError.getMessage());
        }
        for (String metric : List.of("shares", "saved")) {
            try {
                Map<String, Long> one = parseInsights(getMapWithBearer(insightsUrl(igMediaId, metric), accessToken));
                result.putAll(one);
            } catch (Exception e) {
                if (isPermissionDenied(e)) {
                    throw new InsightsPermissionException(e.getMessage());
                }
                log.warn("Insight '{}' unavailable for media {}: {}", metric, igMediaId, e.getMessage());
            }
        }
        return result;
    }

    private String insightsUrl(String igMediaId, String metric) {
        return UriComponentsBuilder
                .fromUriString(fbApi("/" + igMediaId + "/insights"))
                .queryParam("metric", metric)
                .toUriString();
    }

    private static Map<String, Long> parseInsights(Map<String, Object> node) {
        Map<String, Long> result = new LinkedHashMap<>();
        Object rawData = node == null ? null : node.get("data");
        if (!(rawData instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = map.get("name") == null ? null : map.get("name").toString();
            Long value = null;
            Object values = map.get("values");
            if (values instanceof List<?> valueList && !valueList.isEmpty()
                    && valueList.getFirst() instanceof Map<?, ?> valueMap
                    && valueMap.get("value") != null) {
                value = toLong(valueMap.get("value"));
            } else if (map.get("value") != null) {
                value = toLong(map.get("value"));
            } else {
                Object totalValue = map.get("total_value");
                if (totalValue instanceof Map<?, ?> totalMap && totalMap.get("value") != null) {
                    value = toLong(totalMap.get("value"));
                }
            }
            if (name != null && value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    private static boolean isPermissionDenied(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("(#10)")
                || msg.contains("does not have permission")
                || msg.contains("permission");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchMediaComments(String igMediaId, String accessToken) {
        String url = UriComponentsBuilder
                .fromUriString(fbApi("/" + igMediaId + "/comments"))
                .queryParam("fields", COMMENT_FIELDS)
                .queryParam("limit", 50)
                .toUriString();
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            Map<String, Object> node = getMapWithBearer(url, accessToken);
            Object rawData = node == null ? null : node.get("data");
            if (rawData instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        data.add(new LinkedHashMap<>((Map<String, Object>) map));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Comments unavailable for media {}: {}", igMediaId, e.getMessage());
        }
        return data;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
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
        String shortLived = stringVal(node, "access_token");
        if (shortLived == null || shortLived.isBlank()) {
            throw new IllegalStateException("Facebook Graph code exchange returned no access_token");
        }
        try {
            return exchangeFacebookLongLived(shortLived);
        } catch (Exception ex) {
            log.warn("fb_exchange_token failed, keeping short-lived FB token: {}", ex.getMessage());
            return new TokenExchangeResult(shortLived, longVal(node, "expires_in"));
        }
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

    private TokenExchangeResult exchangeFacebookLongLived(String shortLivedToken) {
        String url = UriComponentsBuilder
                .fromUriString(fbApi("/oauth/access_token"))
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", properties.getAppId())
                .queryParam("client_secret", properties.getAppSecret())
                .queryParam("fb_exchange_token", shortLivedToken)
                .toUriString();
        Map<String, Object> node = getMap(url);
        String token = stringVal(node, "access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("fb_exchange_token returned empty access_token");
        }
        return new TokenExchangeResult(token, longVal(node, "expires_in"));
    }

    private Map<String, Object> getMap(String url) {
        // URI.create avoids RestClient treating {…} Graph field expanders as URI templates
        // and double-encoding query values.
        return restClient.get().uri(URI.create(url)).retrieve().body(MAP_TYPE);
    }

    @Override
    public String createMediaContainer(String igUserId, String accessToken, Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (params != null) {
            params.forEach(form::add);
        }
        Map<String, Object> node = postFormWithBearer(fbApi("/" + igUserId + "/media"), accessToken, form);
        String id = stringVal(node, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Graph media container create returned no id: " + node);
        }
        return id;
    }

    @Override
    public ContainerStatus fetchContainerStatus(String containerId, String accessToken) {
        String url = UriComponentsBuilder
                .fromUriString(fbApi("/" + containerId))
                .queryParam("fields", "status_code,status")
                .toUriString();
        Map<String, Object> node = getMapWithBearer(url, accessToken);
        return new ContainerStatus(stringVal(node, "status_code"), stringVal(node, "status"));
    }

    @Override
    public String publishMedia(String igUserId, String accessToken, String creationId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", creationId);
        Map<String, Object> node = postFormWithBearer(fbApi("/" + igUserId + "/media_publish"), accessToken, form);
        String id = stringVal(node, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Graph media_publish returned no id: " + node);
        }
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PublishingLimit fetchContentPublishingLimit(String igUserId, String accessToken) {
        String url = UriComponentsBuilder
                .fromUriString(fbApi("/" + igUserId + "/content_publishing_limit"))
                .queryParam("fields", "config,quota_usage")
                .toUriString();
        Map<String, Object> node = getMapWithBearer(url, accessToken);
        Object dataObj = node == null ? null : node.get("data");
        if (!(dataObj instanceof List<?> data) || data.isEmpty()) {
            return new PublishingLimit(100, 0);
        }
        Object first = data.getFirst();
        if (!(first instanceof Map<?, ?> row)) {
            return new PublishingLimit(100, 0);
        }
        int usage = 0;
        Object usageObj = row.get("quota_usage");
        if (usageObj instanceof Number number) {
            usage = number.intValue();
        } else if (usageObj != null) {
            usage = Integer.parseInt(usageObj.toString());
        }
        int total = 100;
        Object configObj = row.get("config");
        if (configObj instanceof Map<?, ?> config) {
            Object quotaTotal = config.get("quota_total");
            if (quotaTotal instanceof Number number) {
                total = number.intValue();
            } else if (quotaTotal != null) {
                total = Integer.parseInt(quotaTotal.toString());
            }
        }
        return new PublishingLimit(total, usage);
    }

    /** Unified Login SUAT discovery prefers Authorization Bearer (Meta docs / Adeli). */
    private Map<String, Object> getMapWithBearer(String urlWithoutToken, String accessToken) {
        return restClient.get()
                .uri(URI.create(urlWithoutToken))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MAP_TYPE);
    }

    private Map<String, Object> postFormWithBearer(
            String urlWithoutToken,
            String accessToken,
            MultiValueMap<String, String> form
    ) {
        return restClient.post()
                .uri(URI.create(urlWithoutToken))
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);
    }

    private static void addAll(LinkedHashMap<String, IgUserProfile> byId, List<IgUserProfile> profiles) {
        for (IgUserProfile profile : profiles) {
            if (profile.id() == null || profile.id().isBlank()) {
                continue;
            }
            byId.putIfAbsent(profile.id(), profile);
        }
    }

    private List<IgUserProfile> safeList(java.util.function.Supplier<List<IgUserProfile>> supplier) {
        try {
            List<IgUserProfile> list = supplier.get();
            return list != null ? list : List.of();
        } catch (Exception ex) {
            log.warn("Instagram account discovery step failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<IgUserProfile> listFromDebugToken(String userAccessToken) {
        String appToken = properties.getAppId() + "|" + properties.getAppSecret();
        String url = fbApi("/debug_token")
                + "?input_token=" + URLEncoder.encode(userAccessToken, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(appToken, StandardCharsets.UTF_8);
        Map<String, Object> root = getMap(url);
        Object dataObj = root == null ? null : root.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return List.of();
        }
        log.warn("Unified Login debug_token type={} app_id={} user_id={} is_valid={} scopes={} granular_scopes={}",
                data.get("type"), data.get("app_id"), data.get("user_id"), data.get("is_valid"),
                data.get("scopes"), data.get("granular_scopes"));
        Object granular = data.get("granular_scopes");
        if (!(granular instanceof List<?> scopes)) {
            return List.of();
        }
        List<IgUserProfile> out = new ArrayList<>();
        for (Object scopeItem : scopes) {
            if (!(scopeItem instanceof Map<?, ?> scopeMap)) {
                continue;
            }
            String scope = scopeMap.get("scope") == null ? "" : scopeMap.get("scope").toString();
            Object targets = scopeMap.get("target_ids");
            if (!(targets instanceof List<?> ids) || ids.isEmpty()) {
                continue;
            }
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
                    out.add(profile.get());
                } else if (likelyIg) {
                    out.add(new IgUserProfile(candidateId, null, "BUSINESS"));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<IgUserProfile> listFromClientBusiness(String accessToken) {
        String meUrl = fbApi("/me")
                + "?fields=" + URLEncoder.encode("id,name,client_business_id", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        Map<String, Object> me = getMap(meUrl);
        String clientBusinessId = stringVal(me, "client_business_id");
        log.warn("Unified Login /me id={} client_business_id={}", stringVal(me, "id"), clientBusinessId);
        if (clientBusinessId == null || clientBusinessId.isBlank()) {
            return List.of();
        }
        List<IgUserProfile> out = new ArrayList<>();
        for (String edge : List.of(
                "owned_instagram_accounts",
                "client_instagram_accounts",
                "instagram_accounts",
                "instagram_business_accounts"
        )) {
            out.addAll(listIgFromList(
                    fbApi("/" + clientBusinessId + "/" + edge),
                    accessToken,
                    "id,username"
            ));
        }
        return out;
    }

    private List<IgUserProfile> listFromInstagramGraphMe(String accessToken) {
        for (String fields : List.of("user_id,username,account_type", "id,username,account_type")) {
            String url = UriComponentsBuilder
                    .fromUriString(igApi("/me"))
                    .queryParam("fields", fields)
                    .queryParam("access_token", accessToken)
                    .toUriString();
            Map<String, Object> node = getMap(url);
            String id = firstNonBlank(stringVal(node, "user_id"), stringVal(node, "id"));
            if (id != null && !id.isBlank()) {
                return List.of(new IgUserProfile(
                        id,
                        stringVal(node, "username"),
                        stringVal(node, "account_type")
                ));
            }
        }
        return List.of();
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
    private List<IgUserProfile> listFromFacebookPages(String accessToken) {
        String url = fbApi("/me/accounts")
                + "?fields=" + URLEncoder.encode(
                "id,name,access_token,instagram_business_account{id,username}", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        Map<String, Object> node = getMap(url);
        Object rawData = node == null ? null : node.get("data");
        if (!(rawData instanceof List<?> list)) {
            return List.of();
        }
        List<IgUserProfile> out = new ArrayList<>();
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
                    out.add(new IgUserProfile(
                            id,
                            stringVal(account, "username"),
                            "BUSINESS",
                            pageToken
                    ));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<IgUserProfile> listFromBusinesses(String accessToken) {
        String businessesUrl = UriComponentsBuilder
                .fromUriString(fbApi("/me/businesses"))
                .queryParam("fields", "id,name")
                .queryParam("access_token", accessToken)
                .toUriString();
        Map<String, Object> businesses = getMap(businessesUrl);
        Object raw = businesses == null ? null : businesses.get("data");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<IgUserProfile> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> biz)) {
                continue;
            }
            String businessId = biz.get("id") == null ? null : biz.get("id").toString();
            if (businessId == null || businessId.isBlank()) {
                continue;
            }
            for (String edge : List.of("owned_instagram_accounts", "client_instagram_accounts", "instagram_accounts")) {
                out.addAll(listIgFromList(
                        fbApi("/" + businessId + "/" + edge),
                        accessToken,
                        "id,username"
                ));
            }
        }
        return out;
    }

    private List<IgUserProfile> listIgFromList(String path, String accessToken, String fields) {
        String url = path
                + (path.contains("?") ? "&" : "?")
                + "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        return parseIgListAll(path, getMap(url));
    }

    private List<IgUserProfile> listIgFromListBearer(String path, String accessToken, String fields) {
        String url = path
                + (path.contains("?") ? "&" : "?")
                + "fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8);
        return parseIgListAll(path + " (bearer)", getMapWithBearer(url, accessToken));
    }

    @SuppressWarnings("unchecked")
    private List<IgUserProfile> parseIgListAll(String pathLabel, Map<String, Object> node) {
        Object rawData = node == null ? null : node.get("data");
        if (!(rawData instanceof List<?> list) || list.isEmpty()) {
            log.warn("Unified Login list empty for {}", pathLabel);
            return List.of();
        }
        List<IgUserProfile> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> account = new LinkedHashMap<>((Map<String, Object>) row);
            String id = stringVal(account, "id");
            if (id != null && !id.isBlank()) {
                out.add(new IgUserProfile(id, stringVal(account, "username"), "BUSINESS"));
            }
        }
        return out;
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
