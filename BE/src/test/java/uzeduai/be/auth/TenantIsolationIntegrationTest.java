package uzeduai.be.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userCannotAccessForeignTenantWorkspace() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        MvcResult ownerLogin = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"owner-%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"Owner User",
                                  "tenantName":"Owner Tenant %s",
                                  "tenantSlug":"owner-%s"
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String ownerBody = ownerLogin.getResponse().getContentAsString();
        String ownerWorkspaceId = extract(ownerBody, "activeWorkspaceId");
        String ownerTenantId = extract(ownerBody, "activeTenantId");

        MvcResult strangerLogin = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"stranger-%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"Stranger User",
                                  "tenantName":"Stranger Tenant %s",
                                  "tenantSlug":"stranger-%s"
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk())
                .andReturn();

        String strangerToken = extract(strangerLogin.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(post("/api/v1/auth/workspaces/" + ownerWorkspaceId + "/activate")
                        .with(csrf())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/workspaces")
                        .param("tenantId", ownerTenantId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","name":"Hijack"}
                                """.formatted(ownerTenantId)))
                .andExpect(status().isForbidden());
    }

    private String extract(String json, String field) {
        return json.replaceAll("(?s).*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
