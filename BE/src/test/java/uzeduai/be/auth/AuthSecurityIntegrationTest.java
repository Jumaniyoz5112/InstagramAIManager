package uzeduai.be.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uzeduai.be.auth.repository.RefreshTokenRepository;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void bootstrapAdminWorksInDevLikeTestProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@local.dev","password":"0110"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value("admin@local.dev"))
                .andExpect(cookie().exists("iam_refresh"));
    }

    @Test
    void refreshRotatesTokenAndInvalidatesPrevious() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie oldRefresh = requireRefreshCookie(login);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("iam_refresh"))
                .andReturn();

        Cookie newRefresh = requireRefreshCookie(refreshed);
        assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messageKey").value("error.auth.refresh_reuse"));
    }

    @Test
    void revokedTokenReplayRevokesActiveFamily() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie first = requireRefreshCookie(login);

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(first))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = requireRefreshCookie(rotated);

        String email = "sec-" + suffix + "@test.local";
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        long activeBeforeReplay = refreshTokenRepository.countActiveForUser(user.getId(), Instant.now());
        assertThat(activeBeforeReplay).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messageKey").value("error.auth.refresh_reuse"));

        assertThat(refreshTokenRepository.countActiveForUser(user.getId(), Instant.now())).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie refresh = requireRefreshCookie(login);

        User user = userRepository.findByEmailIgnoreCase("sec-" + suffix + "@test.local").orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messageKey").value("error.auth.user_disabled"));

        assertThat(refreshTokenRepository.countActiveForUser(user.getId(), Instant.now())).isZero();
    }

    @Test
    void parallelRefreshDoesNotForkTokens() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie refresh = requireRefreshCookie(login);

        User user = userRepository.findByEmailIgnoreCase("sec-" + suffix + "@test.local").orElseThrow();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            Future<?> a = pool.submit(() -> runParallelRefresh(refresh, start, successes, failures));
            Future<?> b = pool.submit(() -> runParallelRefresh(refresh, start, successes, failures));
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(refreshTokenRepository.countActiveForUser(user.getId(), Instant.now())).isEqualTo(1);

        // Original cookie must no longer mint a new fork
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshWithoutAccessToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie refresh = requireRefreshCookie(login);

        User user = userRepository.findByEmailIgnoreCase("sec-" + suffix + "@test.local").orElseThrow();
        assertThat(refreshTokenRepository.countActiveForUser(user.getId(), Instant.now())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("iam_refresh", 0));

        assertThat(refreshTokenRepository.countActiveForUser(user.getId(), Instant.now())).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshAndLogoutRequireCsrf() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie refresh = requireRefreshCookie(login);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh))
                .andExpect(status().isForbidden());

        // Token remains usable after CSRF rejection
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .cookie(refresh))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenStoredHashedNotRaw() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult login = register(suffix);
        Cookie refresh = requireRefreshCookie(login);

        String expectedHash = sha256(refresh.getValue());
        assertThat(refreshTokenRepository.findByTokenHash(expectedHash)).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(refresh.getValue())).isEmpty();
    }

    private void runParallelRefresh(
            Cookie refresh,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger failures
    ) {
        try {
            start.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(csrf())
                            .cookie(refresh))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        } catch (Exception e) {
            failures.incrementAndGet();
            throw new RuntimeException(e);
        }
    }

    private MvcResult register(String suffix) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"sec-%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"Security User",
                                  "tenantName":"Sec Tenant %s",
                                  "tenantSlug":"sec-%s"
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("iam_refresh"))
                .andReturn();
    }

    private static Cookie requireRefreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("iam_refresh");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    private static String sha256(String raw) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
