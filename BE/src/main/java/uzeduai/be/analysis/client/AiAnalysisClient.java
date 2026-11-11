package uzeduai.be.analysis.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uzeduai.be.analysis.config.AiServiceProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisClient.class);

    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiAnalysisClient(AiServiceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JsonMapper jsonMapper = objectMapper instanceof JsonMapper jm ? jm : JsonMapper.builder().build();
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter(jsonMapper);
        jsonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")
        ));

        // HttpURLConnection avoids JDK HttpClient "Upgrade" / empty-body quirks against uvicorn.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .configureMessageConverters(client -> client
                        .registerDefaults()
                        .withJsonConverter(jsonConverter))
                .build();
    }

    public boolean isHealthy() {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return body != null && "UP".equalsIgnoreCase(String.valueOf(body.get("status")));
        } catch (Exception e) {
            log.debug("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mediaId", request.mediaId() != null ? request.mediaId() : "");
        payload.put("mediaType", request.mediaType() != null ? request.mediaType() : "IMAGE");
        payload.put("caption", request.caption() != null ? request.caption() : "");
        payload.put("imageUrls", request.imageUrls() != null ? request.imageUrls() : List.of());

        byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);

        Map<String, Object> body = restClient.post()
                .uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes)
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("AI service returned empty body");
        }
        String schemaVersion = String.valueOf(body.getOrDefault("schemaVersion", "1.0"));
        Object resultObj = body.get("result");
        Map<String, Object> result = resultObj instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : body;
        String model = body.get("model") != null ? String.valueOf(body.get("model")) : properties.getModelName();
        return new AnalyzeResponse(schemaVersion, model, result);
    }

    @SuppressWarnings("unchecked")
    public AnalyzeResponse insights(Map<String, Object> payload) {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
        Map<String, Object> body = restClient.post()
                .uri("/insights")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes)
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("AI insights returned empty body");
        }
        return mapAiResponse(body, properties.getTextModelName());
    }

    @SuppressWarnings("unchecked")
    public AnalyzeResponse scenario(Map<String, Object> payload) {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
        Map<String, Object> body = restClient.post()
                .uri("/scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes)
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("AI scenario returned empty body");
        }
        return mapAiResponse(body, properties.getTextModelName());
    }

    @SuppressWarnings("unchecked")
    private AnalyzeResponse mapAiResponse(Map<String, Object> body, String defaultModel) {
        String schemaVersion = String.valueOf(body.getOrDefault("schemaVersion", "1.0"));
        Object resultObj = body.get("result");
        Map<String, Object> result = resultObj instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : body;
        String model = body.get("model") != null ? String.valueOf(body.get("model")) : defaultModel;
        return new AnalyzeResponse(schemaVersion, model, result);
    }

    public record AnalyzeRequest(
            String mediaId,
            String mediaType,
            String caption,
            List<String> imageUrls
    ) {
    }

    public record AnalyzeResponse(
            String schemaVersion,
            String model,
            Map<String, Object> result
    ) {
    }
}
