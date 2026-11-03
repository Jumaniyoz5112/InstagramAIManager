package uzeduai.be.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import uzeduai.be.InstagramAiManagerApplication;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecurityFailFastTest {

    private static final String VALID_CRYPTO =
            "prod-test-crypto-key-instagram-ai-manager-ok32";

    @Test
    void productionFailsFastWhenJwtSecretMissing() {
        Exception ex = assertThrows(Exception.class, () -> new SpringApplicationBuilder(InstagramAiManagerApplication.class)
                .profiles("prod")
                .properties(
                        "spring.main.web-application-type=none",
                        "spring.main.banner-mode=off",
                        "DB_URL=jdbc:postgresql://localhost:5432/InstagramAIManager",
                        "DB_USER=postgres",
                        "DB_PASSWORD=0110",
                        "CORS_ALLOWED_ORIGINS=http://localhost:4200"
                )
                // Command-line args beat OS env / profile placeholders
                .run(
                        "--app.security.jwt.secret=",
                        "--IG_TOKEN_ENCRYPTION_KEY=" + VALID_CRYPTO
                ));

        assertTrue(
                containsMessage(ex, "JWT_SECRET")
                        || containsMessage(ex, "Could not resolve placeholder")
                        || containsMessage(ex, "JWT secret missing")
                        || containsMessage(ex, "must be at least 32 bytes"),
                () -> "Expected JWT secret fail-fast, got: " + ex
        );
    }

    @Test
    void productionFailsFastWhenDevJwtSecretUsed() {
        Exception ex = assertThrows(Exception.class, () -> new SpringApplicationBuilder(InstagramAiManagerApplication.class)
                .profiles("prod")
                .properties(
                        "spring.main.web-application-type=none",
                        "spring.main.banner-mode=off",
                        "DB_URL=jdbc:postgresql://localhost:5432/InstagramAIManager",
                        "DB_USER=postgres",
                        "DB_PASSWORD=0110",
                        "CORS_ALLOWED_ORIGINS=http://localhost:4200"
                )
                .run(
                        "--JWT_SECRET=dev-only-change-me-instagram-ai-manager-jwt-secret-32b",
                        "--IG_TOKEN_ENCRYPTION_KEY=" + VALID_CRYPTO
                ));

        assertTrue(
                containsMessage(ex, "Production must not use the development JWT secret"),
                () -> "Expected prod JWT secret rejection, got: " + ex
        );
    }

    private static boolean containsMessage(Throwable error, String fragment) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
