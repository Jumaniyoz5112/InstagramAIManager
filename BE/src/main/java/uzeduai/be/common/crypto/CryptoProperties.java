package uzeduai.be.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.crypto")
public class CryptoProperties {

    /**
     * Base64 or raw secret for AES-GCM (min 32 bytes when UTF-8 encoded).
     * Env: IG_TOKEN_ENCRYPTION_KEY / APP_SECURITY_CRYPTO_SECRET
     */
    private String secret = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
