package uzeduai.be.analysis.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShotImageClient {

    private static final Logger log = LoggerFactory.getLogger(ShotImageClient.class);
    private static final Pattern RETRY_AFTER = Pattern.compile("Retry after (\\d+)");

    private final Object gate = new Object();
    private long nextRequestAt;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String baseUrl;
    private final String model;

    public ShotImageClient(
            @Value("${app.video.image-base-url:https://image.pollinations.ai}") String baseUrl,
            @Value("${app.video.image-model:flux}") String model
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model == null || model.isBlank() ? "flux" : model.trim();
    }

    public BufferedImage fetch(String scene, int seed) {
        String prompt = "Photorealistic vertical 9:16 photograph, natural light, sharp, no text, no letters, no watermark, no logo. Scene: "
                + trim(scene, 420);
        String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/prompt/" + encoded
                + "?width=512&height=896&nologo=true&enhance=false&model=" + model
                + "&seed=" + seed);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(70))
                .header("Accept", "image/jpeg,image/png,image/*")
                .GET()
                .build();
        synchronized (gate) {
            return fetchPaced(request);
        }
    }

    private BufferedImage fetchPaced(HttpRequest request) {
        try {
            for (int attempt = 0; attempt < 10; attempt++) {
                long pause = nextRequestAt - System.currentTimeMillis();
                if (pause > 0) {
                    log.info("Image API pace, waiting {}s before the next shot", (pause + 999) / 1000);
                    Thread.sleep(pause);
                }
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 402 || status == 429 || status == 503) {
                    long wait = status == 402 ? waitSeconds(response) : 8;
                    nextRequestAt = System.currentTimeMillis() + wait * 1000L;
                    log.info("Image API HTTP {}, waiting {}s before the next shot", status, wait);
                    continue;
                }
                if (response.statusCode() >= 400 || response.body() == null || response.body().length < 1000) {
                    throw new IllegalStateException("image API HTTP " + response.statusCode());
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) {
                    throw new IllegalStateException("image API returned a non-image body");
                }
                nextRequestAt = System.currentTimeMillis() + 47_000L;
                return image;
            }
            throw new IllegalStateException("image API HTTP 402");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("image API interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("image API failed: " + ex.getMessage(), ex);
        }
    }

    private static long waitSeconds(HttpResponse<byte[]> response) {
        String header = response.headers().firstValue("retry-after").orElse("");
        if (!header.isBlank()) {
            try {
                return Math.min(90, Long.parseLong(header.trim()) + 2);
            } catch (NumberFormatException ignored) {
                // body text carries the same hint
            }
        }
        String body = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
        Matcher matcher = RETRY_AFTER.matcher(body);
        if (matcher.find()) {
            return Math.min(90, Long.parseLong(matcher.group(1)) + 2);
        }
        return 47;
    }

    private static String trim(String value, int max) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    public int seedFor(String scene, int index) {
        return Math.floorMod((scene + "|" + index).hashCode(), 1_000_000) + index;
    }

    public String modelName() {
        return model.toLowerCase(Locale.ROOT);
    }
}
