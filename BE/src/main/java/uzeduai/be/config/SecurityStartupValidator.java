package uzeduai.be.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uzeduai.be.common.crypto.CryptoProperties;
import uzeduai.be.security.jwt.JwtProperties;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);
    private static final String FORBIDDEN_DEV_SECRET = "dev-only-change-me-instagram-ai-manager-jwt-secret-32b";
    private static final String FORBIDDEN_DEV_CRYPTO = "dev-only-crypto-key-instagram-ai-manager-32b";

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final CryptoProperties cryptoProperties;

    public SecurityStartupValidator(
            Environment environment,
            JwtProperties jwtProperties,
            CryptoProperties cryptoProperties
    ) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.cryptoProperties = cryptoProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = jwtProperties.getSecret() == null ? "" : jwtProperties.getSecret().trim();
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secret.isBlank() || secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret missing or too short (min 32 bytes). Set JWT_SECRET / app.security.jwt.secret");
        }

        String crypto = cryptoProperties.getSecret() == null ? "" : cryptoProperties.getSecret().trim();
        if (crypto.isBlank() || crypto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Crypto secret missing or too short (min 32 bytes). Set IG_TOKEN_ENCRYPTION_KEY");
        }

        boolean prod = Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equals);
        if (!prod) {
            return;
        }

        boolean dropFirst = environment.getProperty("spring.liquibase.drop-first", Boolean.class, false);
        if (FORBIDDEN_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException("Production must not use the development JWT secret");
        }
        if (FORBIDDEN_DEV_CRYPTO.equals(crypto)) {
            throw new IllegalStateException("Production must not use the development crypto secret");
        }
        if (dropFirst) {
            throw new IllegalStateException("spring.liquibase.drop-first must be false in production");
        }
        if (!jwtProperties.isRefreshCookieSecure()) {
            throw new IllegalStateException("app.security.jwt.refresh-cookie-secure must be true in production");
        }
        log.info("Production security checks passed");
    }
}
