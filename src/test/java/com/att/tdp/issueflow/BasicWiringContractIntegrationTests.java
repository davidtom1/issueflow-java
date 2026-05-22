package com.att.tdp.issueflow;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.enums.UserRole;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BasicWiringContractIntegrationTests {

    private static final AtomicLong COUNTER = new AtomicLong();
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void postUsersWithValidBodyReturnsUserProfile() throws Exception {
        String username = unique("valid_user");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "DEVELOPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void unauthenticatedDeveloperCreationWorks() throws Exception {
        String username = unique("public_developer");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "DEVELOPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void unauthenticatedAdminCreationIsBlockedWhenUsersAlreadyExist() throws Exception {
        createUserAndLogin("DEVELOPER");
        String username = unique("public_admin_blocked");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedDeveloperCannotCreateAdmin() throws Exception {
        AuthenticatedTestUser developer = createUserAndLogin("DEVELOPER");
        String username = unique("developer_admin_blocked");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, developer.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAdminCanCreateAdmin() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        String username = unique("admin_created_admin");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void postUsersWithDuplicateUsernameReturnsConflict() throws Exception {
        String username = unique("duplicate_username");
        createUser(username, username + "@example.com", "DEVELOPER");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, unique("other") + "@example.com", "DEVELOPER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void postUsersWithDuplicateEmailReturnsConflict() throws Exception {
        String email = unique("duplicate_email") + "@example.com";
        createUser(unique("first"), email, "DEVELOPER");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(unique("second"), email, "DEVELOPER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void postUsersMissingRequiredFieldReturnsBadRequest() throws Exception {
        String username = unique("missing_email");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "fullName", "Test User",
                                "password", PASSWORD,
                                "role", "DEVELOPER"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postUsersWithInvalidRoleEnumReturnsBadRequest() throws Exception {
        String username = unique("invalid_role");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, username + "@example.com", "MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUsersByNonexistentIdReturnsNotFound() throws Exception {
        AuthenticatedTestUser user = createUserAndLogin("DEVELOPER");

        mockMvc.perform(get("/users/999999999")
                        .header(HttpHeaders.AUTHORIZATION, user.bearerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void authMeWithoutAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authMeWithValidBearerTokenReturnsCurrentUserProfile() throws Exception {
        AuthenticatedTestUser user = createUserAndLogin("DEVELOPER");

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, user.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void logoutInvalidatesToken() throws Exception {
        AuthenticatedTestUser user = createUserAndLogin("DEVELOPER");

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, user.bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, user.bearerToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postProjectsWithValidOwnerCreatesProjectAndOwnerMembership() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");

        MvcResult projectResult = createProject(admin, unique("project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(get("/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) admin.id())))
                .andExpect(jsonPath("$[*].username", hasItem(admin.username())));
    }

    @Test
    void getProjectsByNonexistentIdReturnsNotFound() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");

        mockMvc.perform(get("/projects/999999999")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProjectsSoftDeletesProjectAndHidesItFromStandardProjectReads() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        MvcResult projectResult = createProject(admin, unique("soft_delete_project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(delete("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) projectId))));

        mockMvc.perform(get("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isNotFound());
    }

    @Test // revise after implementing restore endpoint - this is just to confirm the contract of soft delete and restore when the restore endpoint is implemented
    void postProjectsRestoreRestoresSoftDeletedProjectWhenEndpointExists() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        MvcResult projectResult = createProject(admin, unique("restore_project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(delete("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());

        MvcResult restoreResult = mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andReturn();

        Assumptions.assumeTrue(
                restoreResult.getResponse().getStatus() == 200,
                "Project restore endpoint is not implemented yet"
        );

        mockMvc.perform(get("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));
    }

    @Test
    void adminCanGetDeletedProjects() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");

        mockMvc.perform(get("/projects/deleted")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanRestoreSoftDeletedProject() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        MvcResult projectResult = createProject(admin, unique("admin_restore_project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(delete("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));
    }

    @Test
    void developerCannotGetDeletedProjects() throws Exception {
        AuthenticatedTestUser developer = createUserAndLogin("DEVELOPER");

        mockMvc.perform(get("/projects/deleted")
                        .header(HttpHeaders.AUTHORIZATION, developer.bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotRestoreSoftDeletedProject() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        AuthenticatedTestUser developer = createUserAndLogin("DEVELOPER");
        MvcResult projectResult = createProject(admin, unique("developer_restore_forbidden_project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(delete("/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, developer.bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotGetDeletedTicketsByProject() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        AuthenticatedTestUser developer = createUserAndLogin("DEVELOPER");
        MvcResult projectResult = createProject(admin, unique("developer_deleted_tickets_forbidden_project"));
        long projectId = idFrom(projectResult);

        mockMvc.perform(get("/tickets/deleted")
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, developer.bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ticketDependenciesHappyPathAddsAndListsDependency() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        MvcResult projectResult = createProject(admin, unique("dependency_project"));
        long projectId = idFrom(projectResult);

        MvcResult blockedTicketResult = createTicket(admin, projectId, "Blocked ticket", "TODO");
        MvcResult blockerTicketResult = createTicket(admin, projectId, "Blocking ticket", "IN_PROGRESS");
        long blockedTicketId = idFrom(blockedTicketResult);
        long blockerTicketId = idFrom(blockerTicketResult);

        mockMvc.perform(post("/tickets/" + blockedTicketId + "/dependencies")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("blockedBy", blockerTicketId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + blockedTicketId + "/dependencies")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + blockerTicketId + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + blockerTicketId + ")].title").value(hasItem("Blocking ticket")))
                .andExpect(jsonPath("$[?(@.id == " + blockerTicketId + ")].status").value(hasItem("IN_PROGRESS")));
    }

    @Test
    void ticketAuditLogsSeparateStatusChangesFromNonStatusUpdates() throws Exception {
        AuthenticatedTestUser admin = createUserAndLogin("ADMIN");
        MvcResult projectResult = createProject(admin, unique("ticket_audit_project"));
        long projectId = idFrom(projectResult);

        MvcResult statusOnlyTicketResult = createTicket(admin, projectId, unique("status_only_ticket"), "TODO");
        long statusOnlyTicketId = idFrom(statusOnlyTicketResult);

        mockMvc.perform(patch("/tickets/" + statusOnlyTicketId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        MvcResult statusOnlyAuditResult = mockMvc.perform(get("/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .param("entityType", "TICKET")
                        .param("entityId", String.valueOf(statusOnlyTicketId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode statusOnlyLogs = objectMapper.readTree(statusOnlyAuditResult.getResponse().getContentAsString());
        boolean statusOnlyHasStatusChange = false;
        boolean statusOnlyHasUpdate = false;
        for (JsonNode log : statusOnlyLogs) {
            String action = log.get("action").asText();
            statusOnlyHasStatusChange = statusOnlyHasStatusChange || "STATUS_CHANGE".equals(action);
            statusOnlyHasUpdate = statusOnlyHasUpdate || "UPDATE".equals(action);
        }
        assertTrue(statusOnlyHasStatusChange);
        assertFalse(statusOnlyHasUpdate);

        MvcResult titleOnlyTicketResult = createTicket(admin, projectId, unique("title_only_ticket"), "TODO");
        long titleOnlyTicketId = idFrom(titleOnlyTicketResult);

        mockMvc.perform(patch("/tickets/" + titleOnlyTicketId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", unique("renamed_ticket")))))
                .andExpect(status().isOk());

        MvcResult titleOnlyAuditResult = mockMvc.perform(get("/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearerToken())
                        .param("entityType", "TICKET")
                        .param("entityId", String.valueOf(titleOnlyTicketId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode titleOnlyLogs = objectMapper.readTree(titleOnlyAuditResult.getResponse().getContentAsString());
        boolean titleOnlyHasUpdate = false;
        boolean titleOnlyHasStatusChange = false;
        for (JsonNode log : titleOnlyLogs) {
            String action = log.get("action").asText();
            titleOnlyHasUpdate = titleOnlyHasUpdate || "UPDATE".equals(action);
            titleOnlyHasStatusChange = titleOnlyHasStatusChange || "STATUS_CHANGE".equals(action);
        }
        assertTrue(titleOnlyHasUpdate);
        assertFalse(titleOnlyHasStatusChange);
    }

    private AuthenticatedTestUser createUserAndLogin(String role) throws Exception {
        String username = unique(role.toLowerCase() + "_user");
        String email = username + "@example.com";
        long id;
        if ("ADMIN".equals(role)) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setFullName("Test User");
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setRole(UserRole.ADMIN);
            id = userRepository.saveAndFlush(user).getId();
        } else {
            MvcResult createResult = createUser(username, email, role);
            id = idFrom(createResult);
        }

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return new AuthenticatedTestUser(
                id,
                username,
                email,
                "Bearer " + login.get("accessToken").asText()
        );
    }

    private MvcResult createUser(String username, String email, String role) throws Exception {
        return mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(userBody(username, email, role))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult createProject(AuthenticatedTestUser owner, String name) throws Exception {
        return mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Integration test project",
                                "ownerId", owner.id()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.ownerId").value(owner.id()))
                .andReturn();
    }

    private MvcResult createTicket(
            AuthenticatedTestUser user,
            long projectId,
            String title,
            String status
    ) throws Exception {
        return mockMvc.perform(post("/tickets")
                        .header(HttpHeaders.AUTHORIZATION, user.bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "projectId", projectId,
                                "title", title,
                                "description", title + " description",
                                "status", status,
                                "priority", "MEDIUM",
                                "type", "FEATURE",
                                "assigneeId", user.id()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andReturn();
    }

    private Map<String, Object> userBody(String username, String email, String role) {
        return Map.of(
                "username", username,
                "email", email,
                "fullName", "Test User",
                "password", PASSWORD,
                "role", role
        );
    }

    private long idFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String unique(String prefix) {
        return prefix + "_" + COUNTER.incrementAndGet() + "_" + System.nanoTime();
    }

    private record AuthenticatedTestUser(long id, String username, String email, String bearerToken) {
    }
}
