package uzeduai.be.instagram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstagramM1IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseCreatedInstagramCoreTablesAndPage() {
        Integer accounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'instagram_accounts'",
                Integer.class
        );
        Integer media = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'instagram_media'",
                Integer.class
        );
        Integer page = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_pages WHERE code = 'INSTAGRAM'",
                Integer.class
        );
        assertThat(accounts).isEqualTo(1);
        assertThat(media).isEqualTo(1);
        assertThat(page).isEqualTo(1);
    }

    @Test
    void oauthStartRequiresAuthAndFailsWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/instagram/oauth/start").with(csrf()))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@local.dev","password":"0110"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = extract(login.getResponse().getContentAsString(), "accessToken");

        // Instagram disabled by default → 503 (or workspace required if enabled later)
        mockMvc.perform(post("/api/v1/instagram/oauth/start")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.messageKey").value("error.instagram.disabled"));
    }

    @Test
    void mediaListRequiresInstagramPageGrant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"ig-%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"IG User",
                                  "tenantName":"IG Tenant %s",
                                  "tenantSlug":"ig-%s"
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andReturn();
        String token = extract(reg.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(get("/api/v1/instagram/media")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageKey").value("error.access.page_forbidden"));
    }

    private static String extract(String json, String field) {
        return json.replaceAll("(?s).*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
