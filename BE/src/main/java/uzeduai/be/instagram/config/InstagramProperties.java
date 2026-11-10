package uzeduai.be.instagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.instagram")
public class InstagramProperties {

    private boolean enabled = false;
    private String appId = "";
    private String appSecret = "";
    /**
     * Meta Facebook Login for Business configuration ID (Unified Login).
     * When set, OAuth uses facebook.com dialog + graph.facebook.com instead of Instagram Login.
     */
    private String configId = "";
    private String redirectUri = "http://localhost:8080/api/v1/instagram/oauth/callback";
    /** Where browser lands after OAuth callback (Angular UI). */
    private String frontendSuccessUrl = "http://localhost:4200/app/instagram";
    private String apiBaseUrl = "https://graph.instagram.com";
    private String authorizeBaseUrl = "https://api.instagram.com/oauth/authorize";
    private String facebookAuthorizeBaseUrl = "https://www.facebook.com";
    private String facebookGraphBaseUrl = "https://graph.facebook.com";
    private String graphVersion = "v21.0";
    private String scopes = "instagram_business_basic";
    private int oauthStateTtlMinutes = 10;
    private int historicalMaxPages = 50;
    private int mediaUrlTtlHours = 48;
    private long tokenRefreshLeadDays = 10;
    private long incrementalSyncIntervalMs = 900_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public boolean isUnifiedLogin() {
        return configId != null && !configId.isBlank();
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getFrontendSuccessUrl() {
        return frontendSuccessUrl;
    }

    public void setFrontendSuccessUrl(String frontendSuccessUrl) {
        this.frontendSuccessUrl = frontendSuccessUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getAuthorizeBaseUrl() {
        return authorizeBaseUrl;
    }

    public void setAuthorizeBaseUrl(String authorizeBaseUrl) {
        this.authorizeBaseUrl = authorizeBaseUrl;
    }

    public String getFacebookAuthorizeBaseUrl() {
        return facebookAuthorizeBaseUrl;
    }

    public void setFacebookAuthorizeBaseUrl(String facebookAuthorizeBaseUrl) {
        this.facebookAuthorizeBaseUrl = facebookAuthorizeBaseUrl;
    }

    public String getFacebookGraphBaseUrl() {
        return facebookGraphBaseUrl;
    }

    public void setFacebookGraphBaseUrl(String facebookGraphBaseUrl) {
        this.facebookGraphBaseUrl = facebookGraphBaseUrl;
    }

    public String getGraphVersion() {
        return graphVersion;
    }

    public void setGraphVersion(String graphVersion) {
        this.graphVersion = graphVersion;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public int getOauthStateTtlMinutes() {
        return oauthStateTtlMinutes;
    }

    public void setOauthStateTtlMinutes(int oauthStateTtlMinutes) {
        this.oauthStateTtlMinutes = oauthStateTtlMinutes;
    }

    public int getHistoricalMaxPages() {
        return historicalMaxPages;
    }

    public void setHistoricalMaxPages(int historicalMaxPages) {
        this.historicalMaxPages = historicalMaxPages;
    }

    public int getMediaUrlTtlHours() {
        return mediaUrlTtlHours;
    }

    public void setMediaUrlTtlHours(int mediaUrlTtlHours) {
        this.mediaUrlTtlHours = mediaUrlTtlHours;
    }

    public long getTokenRefreshLeadDays() {
        return tokenRefreshLeadDays;
    }

    public void setTokenRefreshLeadDays(long tokenRefreshLeadDays) {
        this.tokenRefreshLeadDays = tokenRefreshLeadDays;
    }

    public long getIncrementalSyncIntervalMs() {
        return incrementalSyncIntervalMs;
    }

    public void setIncrementalSyncIntervalMs(long incrementalSyncIntervalMs) {
        this.incrementalSyncIntervalMs = incrementalSyncIntervalMs;
    }
}
