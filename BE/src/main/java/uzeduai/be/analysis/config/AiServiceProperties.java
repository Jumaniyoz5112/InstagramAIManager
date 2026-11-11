package uzeduai.be.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {

    /** When false, content analysis jobs stay queued and are not processed. */
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8000";
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 300_000;
    private String modelName = "qwen2.5vl:7b";
    private String textModelName = "qwen2.5:7b";
    private String promptVersion = "1.1";
    private String insightsPromptVersion = "1.2";
    private String scenarioPromptVersion = "1.3";
    private int insightsTopN = 6;
    private int insightsLowN = 3;
    /** Max concurrent CONTENT analyses (1 recommended for 8GB VRAM). */
    private int maxConcurrent = 1;
    private int queueBatchSize = 1;
    /** Morning/evening engagement capture (Asia/Tashkent). */
    private String engagementCron = "0 0 8,20 * * *";
    private String engagementZone = "Asia/Tashkent";
    private int engagementMediaLimitPerAccount = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getTextModelName() {
        return textModelName;
    }

    public void setTextModelName(String textModelName) {
        this.textModelName = textModelName;
    }

    public String getInsightsPromptVersion() {
        return insightsPromptVersion;
    }

    public void setInsightsPromptVersion(String insightsPromptVersion) {
        this.insightsPromptVersion = insightsPromptVersion;
    }

    public String getScenarioPromptVersion() {
        return scenarioPromptVersion;
    }

    public void setScenarioPromptVersion(String scenarioPromptVersion) {
        this.scenarioPromptVersion = scenarioPromptVersion;
    }

    public int getInsightsTopN() {
        return insightsTopN;
    }

    public void setInsightsTopN(int insightsTopN) {
        this.insightsTopN = insightsTopN;
    }

    public int getInsightsLowN() {
        return insightsLowN;
    }

    public void setInsightsLowN(int insightsLowN) {
        this.insightsLowN = insightsLowN;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getQueueBatchSize() {
        return queueBatchSize;
    }

    public void setQueueBatchSize(int queueBatchSize) {
        this.queueBatchSize = queueBatchSize;
    }

    public String getEngagementCron() {
        return engagementCron;
    }

    public void setEngagementCron(String engagementCron) {
        this.engagementCron = engagementCron;
    }

    public String getEngagementZone() {
        return engagementZone;
    }

    public void setEngagementZone(String engagementZone) {
        this.engagementZone = engagementZone;
    }

    public int getEngagementMediaLimitPerAccount() {
        return engagementMediaLimitPerAccount;
    }

    public void setEngagementMediaLimitPerAccount(int engagementMediaLimitPerAccount) {
        this.engagementMediaLimitPerAccount = engagementMediaLimitPerAccount;
    }
}
