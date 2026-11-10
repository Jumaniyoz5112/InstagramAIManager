package uzeduai.be.instagram.service;

import org.springframework.stereotype.Component;
import uzeduai.be.common.crypto.CryptoProperties;
import uzeduai.be.instagram.config.InstagramProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class PublishMediaUrlSigner {

    private final InstagramProperties instagramProperties;
    private final CryptoProperties cryptoProperties;

    public PublishMediaUrlSigner(InstagramProperties instagramProperties, CryptoProperties cryptoProperties) {
        this.instagramProperties = instagramProperties;
        this.cryptoProperties = cryptoProperties;
    }

    public String buildPublicUrl(UUID assetId) {
        String base = trimTrailingSlash(instagramProperties.getPublicMediaBaseUrl());
        if (base == null || base.isBlank()) {
            return null;
        }
        long exp = Instant.now().getEpochSecond() + Math.max(60, instagramProperties.getPublicMediaUrlTtlSeconds());
        String sig = sign(assetId, exp);
        return base + "/api/v1/instagram/public-media/" + assetId + "?exp=" + exp + "&sig=" + sig;
    }

    public boolean isValid(UUID assetId, long expEpochSeconds, String sig) {
        if (sig == null || sig.isBlank()) {
            return false;
        }
        if (expEpochSeconds < Instant.now().getEpochSecond()) {
            return false;
        }
        String expected = sign(assetId, expEpochSeconds);
        return constantTimeEquals(expected, sig.trim().toLowerCase());
    }

    private String sign(UUID assetId, long expEpochSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes(), "HmacSHA256"));
            byte[] raw = mac.doFinal((assetId + ":" + expEpochSeconds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign public media URL", ex);
        }
    }

    private byte[] secretBytes() {
        String secret = cryptoProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            secret = "dev-only-publish-media-signing-fallback-key-32b";
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
