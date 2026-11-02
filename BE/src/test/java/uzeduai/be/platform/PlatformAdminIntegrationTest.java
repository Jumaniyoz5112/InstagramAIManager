package uzeduai.be.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Test
    void platformAdminCanListAllTenantsIncludingForeign() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult orgA = register("pla-" + suffix, "Platform Org A " + suffix, "pla-" + suffix);
        MvcResult orgB = register("plb-" + suffix, "Platform Org B " + suffix, "plb-" + suffix);
        String tenantA = extract(orgA.getResponse().getContentAsString(), "activeTenantId");
        String tenantB = extract(orgB.getResponse().getContentAsString(), "activeTenantId");

        String adminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(tenantA)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(tenantB)))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void platformAdminCanSearchAndPaginateTenants() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        register("psr-" + suffix, "Searchable Tenant " + suffix, "psr-" + suffix);
        String adminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("q", "Searchable Tenant " + suffix)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "name,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].slug").value("psr-" + suffix))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageKey").value("error.platform.invalid_page_size"));

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("sort", "passwordHash,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageKey").value("error.platform.invalid_sort"));
    }

    @Test
    void platformAdminCanReadTenantDetailsWorkspacesMembersAndStats() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult org = register("pdet-" + suffix, "Detail Tenant " + suffix, "pdet-" + suffix);
        String body = org.getResponse().getContentAsString();
        String tenantId = extract(body, "activeTenantId");
        String workspaceId = extract(body, "activeWorkspaceId");
        String ownerEmail = "pdet-" + suffix + "@test.local";

        String adminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tenantId))
                .andExpect(jsonPath("$.data.slug").value("pdet-" + suffix))
                .andExpect(jsonPath("$.data.memberCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.workspaceCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId + "/workspaces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(workspaceId)))
                .andExpect(jsonPath("$.data[0].tenantId").value(tenantId));

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value(ownerEmail))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].userId").exists())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());

        mockMvc.perform(get("/api/v1/platform/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.userCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.workspaceCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.membershipCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/platform/tenants/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageKey").value("error.platform.tenant_not_found"));
    }

    @Test
    void platformAdminWithoutMembershipCanStillSeeTenant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult org = register("pnom-" + suffix, "No Membership Org " + suffix, "pnom-" + suffix);
        String tenantId = extract(org.getResponse().getContentAsString(), "activeTenantId");

        User admin = userRepository.findByEmailIgnoreCase("admin@local.dev").orElseThrow();
        tenantMemberRepository.findByTenantIdAndUserId(UUID.fromString(tenantId), admin.getId())
                .ifPresent(tenantMemberRepository::delete);
        tenantMemberRepository.flush();

        assertThat(tenantMemberRepository.findByTenantIdAndUserId(UUID.fromString(tenantId), admin.getId()))
                .isEmpty();

        String adminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tenantId));

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId + "/workspaces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/platform/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void ownerAndMemberCannotAccessPlatformApis() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult ownerReg = register("ownp-" + suffix, "Owner Platform " + suffix, "ownp-" + suffix);
        String ownerToken = extract(ownerReg.getResponse().getContentAsString(), "accessToken");
        String ownerTenantId = extract(ownerReg.getResponse().getContentAsString(), "activeTenantId");

        MvcResult memberReg = register("memp-" + suffix, "Member Platform " + suffix, "memp-" + suffix);
        String memberToken = extract(memberReg.getResponse().getContentAsString(), "accessToken");

        User owner = userRepository.findByEmailIgnoreCase("ownp-" + suffix + "@test.local").orElseThrow();
        User member = userRepository.findByEmailIgnoreCase("memp-" + suffix + "@test.local").orElseThrow();

        TenantMember ownerMembership = tenantMemberRepository
                .findByTenantIdAndUserIdWithTenant(UUID.fromString(ownerTenantId), owner.getId())
                .orElseThrow();

        TenantMember membership = new TenantMember();
        membership.setTenant(ownerMembership.getTenant());
        membership.setUser(member);
        membership.setRole(TenantRole.MEMBER);
        tenantMemberRepository.saveAndFlush(membership);

        for (String token : new String[]{ownerToken, memberToken}) {
            mockMvc.perform(get("/api/v1/platform/tenants").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/platform/stats").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/platform/tenants/" + ownerTenantId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unauthenticatedPlatformRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membershipTenantsApiUnchangedWhilePlatformSeesAll() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult owner = register("oreg-" + suffix, "Owner Regression " + suffix, "oreg-" + suffix);
        String ownerToken = extract(owner.getResponse().getContentAsString(), "accessToken");
        String ownerTenantId = extract(owner.getResponse().getContentAsString(), "activeTenantId");

        MvcResult other = register("oreg2-" + suffix, "Other Regression " + suffix, "oreg2-" + suffix);
        String otherTenantId = extract(other.getResponse().getContentAsString(), "activeTenantId");

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        String adminToken = loginAdmin();
        mockMvc.perform(get("/api/v1/platform/tenants")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(ownerTenantId)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(otherTenantId)));

        // Membership-scoped /tenants must not expose foreign orgs via membership list for admin
        User admin = userRepository.findByEmailIgnoreCase("admin@local.dev").orElseThrow();
        assertThat(tenantMemberRepository.findByTenantIdAndUserId(UUID.fromString(ownerTenantId), admin.getId()))
                .isEmpty();

        MvcResult adminTenants = mockMvc.perform(get("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(adminTenants.getResponse().getContentAsString()).doesNotContain(ownerTenantId);
        assertThat(adminTenants.getResponse().getContentAsString()).doesNotContain(otherTenantId);
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@local.dev","password":"0110"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return extract(login.getResponse().getContentAsString(), "accessToken");
    }

    private MvcResult register(String emailPrefix, String tenantName, String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"Platform Test User",
                                  "tenantName":"%s",
                                  "tenantSlug":"%s"
                                }
                                """.formatted(emailPrefix, tenantName, slug)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String extract(String json, String field) {
        return json.replaceAll("(?s).*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
