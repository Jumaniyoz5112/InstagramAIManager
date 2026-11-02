package uzeduai.be.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uzeduai.be.access.entity.AppPage;
import uzeduai.be.access.entity.UserPageAccess;
import uzeduai.be.access.repository.AppPageRepository;
import uzeduai.be.access.repository.UserPageAccessRepository;
import uzeduai.be.common.enums.TenantRole;
import uzeduai.be.tenant.context.TenantContextHolder;
import uzeduai.be.tenant.entity.Tenant;
import uzeduai.be.tenant.entity.TenantMember;
import uzeduai.be.tenant.repository.TenantMemberRepository;
import uzeduai.be.tenant.repository.TenantRepository;
import uzeduai.be.user.entity.User;
import uzeduai.be.user.repository.UserRepository;
import uzeduai.be.workspace.entity.Workspace;
import uzeduai.be.workspace.repository.WorkspaceRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantContextIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private AppPageRepository appPageRepository;

    @Autowired
    private UserPageAccessRepository userPageAccessRepository;

    @Test
    void revokedMembershipClearsActiveTenantOnAccessToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = register("ctx-a-" + suffix, "Ctx A " + suffix, "ctx-a-" + suffix);
        String token = extract(registered.getResponse().getContentAsString(), "accessToken");
        String tenantId = extract(registered.getResponse().getContentAsString(), "activeTenantId");
        String email = "ctx-a-" + suffix + "@test.local";

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        grantPage(user, "WORKSPACES");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeTenantId").value(tenantId));

        TenantMember membership = tenantMemberRepository
                .findByTenantIdAndUserId(UUID.fromString(tenantId), user.getId())
                .orElseThrow();
        tenantMemberRepository.delete(membership);
        tenantMemberRepository.flush();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeTenantId").value(nullValue()));

        mockMvc.perform(get("/api/v1/workspaces")
                        .param("tenantId", tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageKey").value("error.workspace.not_member"));
    }

    @Test
    void foreignWorkspaceIdCannotBeActivated() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult owner = register("own-" + suffix, "Own " + suffix, "own-" + suffix);
        String ownerWorkspaceId = extract(owner.getResponse().getContentAsString(), "activeWorkspaceId");

        MvcResult stranger = register("str-" + suffix, "Str " + suffix, "str-" + suffix);
        String strangerToken = extract(stranger.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(post("/api/v1/auth/workspaces/" + ownerWorkspaceId + "/activate")
                        .with(csrf())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageKey").value("error.auth.workspace_forbidden"));
    }

    @Test
    void tenantContextDoesNotLeakAcrossConcurrentRequests() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult a = register("lea-" + suffix, "Leak A " + suffix, "lea-" + suffix);
        MvcResult b = register("leb-" + suffix, "Leak B " + suffix, "leb-" + suffix);

        String tokenA = extract(a.getResponse().getContentAsString(), "accessToken");
        String tokenB = extract(b.getResponse().getContentAsString(), "accessToken");
        String tenantA = extract(a.getResponse().getContentAsString(), "activeTenantId");
        String tenantB = extract(b.getResponse().getContentAsString(), "activeTenantId");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> bodyA = new AtomicReference<>();
        AtomicReference<String> bodyB = new AtomicReference<>();

        try {
            Future<?> fa = pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                                    .header("Authorization", "Bearer " + tokenA))
                            .andExpect(status().isOk())
                            .andReturn();
                    bodyA.set(result.getResponse().getContentAsString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Future<?> fb = pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                                    .header("Authorization", "Bearer " + tokenB))
                            .andExpect(status().isOk())
                            .andReturn();
                    bodyB.set(result.getResponse().getContentAsString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            start.countDown();
            fa.get(15, TimeUnit.SECONDS);
            fb.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(extract(bodyA.get(), "activeTenantId")).isEqualTo(tenantA);
        assertThat(extract(bodyB.get(), "activeTenantId")).isEqualTo(tenantB);
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void memberCannotCreateWorkspaceButOwnerCan() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult ownerReg = register("owr-" + suffix, "Owner Role " + suffix, "owr-" + suffix);
        String ownerToken = extract(ownerReg.getResponse().getContentAsString(), "accessToken");
        String tenantId = extract(ownerReg.getResponse().getContentAsString(), "activeTenantId");

        MvcResult memberReg = register("mem-" + suffix, "Member Role " + suffix, "mem-" + suffix);
        String memberToken = extract(memberReg.getResponse().getContentAsString(), "accessToken");
        String memberEmail = "mem-" + suffix + "@test.local";

        User owner = userRepository.findByEmailIgnoreCase("owr-" + suffix + "@test.local").orElseThrow();
        User member = userRepository.findByEmailIgnoreCase(memberEmail).orElseThrow();
        Tenant tenant = tenantRepository.findById(UUID.fromString(tenantId)).orElseThrow();

        grantPage(owner, "WORKSPACES");
        grantPage(member, "WORKSPACES");

        TenantMember memberShip = new TenantMember();
        memberShip.setTenant(tenant);
        memberShip.setUser(member);
        memberShip.setRole(TenantRole.MEMBER);
        tenantMemberRepository.saveAndFlush(memberShip);

        mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","name":"Member Attempt"}
                                """.formatted(tenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageKey").value("error.workspace.create_forbidden"));

        mockMvc.perform(post("/api/v1/workspaces")
                        .with(csrf())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","name":"Owner Workspace"}
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Owner Workspace"));
    }

    @Test
    void workspaceMustBelongToMembershipTenant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult userA = register("wsa-" + suffix, "Ws A " + suffix, "wsa-" + suffix);
        String tokenA = extract(userA.getResponse().getContentAsString(), "accessToken");
        String tenantA = extract(userA.getResponse().getContentAsString(), "activeTenantId");

        MvcResult userB = register("wsb-" + suffix, "Ws B " + suffix, "wsb-" + suffix);
        String tenantB = extract(userB.getResponse().getContentAsString(), "activeTenantId");
        String workspaceB = extract(userB.getResponse().getContentAsString(), "activeWorkspaceId");

        // Forge: workspace B id does not belong to tenant A — scoped lookup must fail for activate
        // after discovering tenant B, membership for A on B is forbidden (covered above).
        // Additionally ensure findByIdAndTenantId cannot resolve cross-tenant pair.
        assertThat(workspaceRepository.findByIdAndTenantId(
                UUID.fromString(workspaceB), UUID.fromString(tenantA)
        )).isEmpty();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeTenantId").value(tenantA));

        Workspace wsB = workspaceRepository.findByIdWithTenant(UUID.fromString(workspaceB)).orElseThrow();
        assertThat(wsB.getTenant().getId()).isEqualTo(UUID.fromString(tenantB));
    }

    private MvcResult register(String emailPrefix, String tenantName, String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s@test.local",
                                  "password":"Pass0110!",
                                  "fullName":"Context User",
                                  "tenantName":"%s",
                                  "tenantSlug":"%s"
                                }
                                """.formatted(emailPrefix, tenantName, slug)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void grantPage(User user, String pageCode) {
        boolean already = userPageAccessRepository.findByUserId(user.getId()).stream()
                .anyMatch(a -> a.getPage().getCode().equals(pageCode));
        if (already) {
            return;
        }
        AppPage page = appPageRepository.findById(pageCode).orElseThrow();
        UserPageAccess access = new UserPageAccess();
        access.setUser(user);
        access.setPage(page);
        userPageAccessRepository.saveAndFlush(access);
    }

    private static String extract(String json, String field) {
        return json.replaceAll("(?s).*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
