// Teste de integrare pentru echipă și workspace: asamblare automată, membri, sarcini, repository.
package com.vnhackers.dotconn.projects;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "GROQ_API_KEY=")
@ActiveProfiles("test")
class WorkspaceIntegrationTests {

  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  @Autowired DeveloperProfileRepository profiles;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    profiles.deleteAll(); // Keep recommendations independent from fixtures created by other tests.
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private record Account(String token, Long userId) {}

  private Account signup() throws Exception {
    String email = "ws-" + UUID.randomUUID() + "@example.com";
    String body =
        mapper.writeValueAsString(java.util.Map.of("email", email, "password", "Test-password-123"));
    var result =
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String token = mapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
    User user = users.findByEmail(email).orElseThrow();
    return new Account(token, user.getId());
  }

  private void seedProfile(String token, String display, String role, String techsJson) throws Exception {
    String body =
        """
        {"displayName":"%s","role":"%s","technologies":%s,
         "experienceYears":4,"githubUrl":"https://github.com/dev-%s",
         "availability":"available","hourlyRate":40}
        """
            .formatted(display, role, techsJson, UUID.randomUUID().toString().substring(0, 8));
    mvc.perform(
            patch("/api/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
  }

  private long createProject(String token, String rolesJson, String techsJson) throws Exception {
    String body =
        """
        {"title":"Platforma %s","description":"Am nevoie de backend pentru plati online si notificari.",
         "roles":%s,"tasks":["API plati"],"existingStack":["React"],"requiredTechnologies":%s}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 8), rolesJson, techsJson);
    var result =
        mvc.perform(
                post("/api/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private long invite(String ownerToken, long projectId, long inviteeId) throws Exception {
    var result =
        mvc.perform(
                post("/api/projects/" + projectId + "/invitations")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content("{\"inviteeId\":" + inviteeId + "}"))
            .andExpect(status().isCreated())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private void accept(String token, long invitationId) throws Exception {
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"action\":\"accept\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void autoAssembleInvitesBestCandidates() throws Exception {
    Account owner = signup();
    Account backend = signup();
    Account frontend = signup();
    seedProfile(backend.token(), "Backend Dev", "backend", "[\"Spring\"]");
    seedProfile(frontend.token(), "Frontend Dev", "frontend", "[\"Vue\"]");
    long projectId = createProject(owner.token(), "[\"backend\"]", "[\"Spring\"]");

    // asamblare: invită backend-ul (scor mare), nu frontend-ul
    mvc.perform(
            post("/api/projects/" + projectId + "/auto-assemble")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"maxInvitations\":5,\"minScore\":30}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].status").value("pending"));

    // backend-ul își vede invitația
    mvc.perform(get("/api/me/invitations").header("Authorization", "Bearer " + backend.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].projectId", hasItem((int) projectId)));

    // a doua asamblare nu mai invită pe nimeni (deja invitați săriți) -> []
    mvc.perform(
            post("/api/projects/" + projectId + "/auto-assemble")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"maxInvitations\":5,\"minScore\":30}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()", is(0)));

    // prag imposibil -> []
    mvc.perform(
            post("/api/projects/" + projectId + "/auto-assemble")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"maxInvitations\":5,\"minScore\":100}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()", is(0)));

    // străinul nu poate asambla -> 404; anonim -> 401
    mvc.perform(
            post("/api/projects/" + projectId + "/auto-assemble")
                .header("Authorization", "Bearer " + frontend.token())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/projects/" + projectId + "/auto-assemble")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void membersAndWorkspace() throws Exception {
    Account owner = signup();
    Account dev = signup();
    Account stranger = signup();
    seedProfile(dev.token(), "Dev Member", "backend", "[\"Spring\"]");
    long projectId = createProject(owner.token(), "[\"backend\"]", "[\"Spring\"]");

    // înainte de accept: doar proprietarul e membru
    mvc.perform(
            get("/api/projects/" + projectId + "/members")
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(1)))
        .andExpect(jsonPath("$[0].owner").value(true));

    long invitationId = invite(owner.token(), projectId, dev.userId());
    accept(dev.token(), invitationId);

    // după accept: 2 membri, cu profil îmbogățit
    mvc.perform(
            get("/api/projects/" + projectId + "/members")
                .header("Authorization", "Bearer " + dev.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(2)))
        .andExpect(jsonPath("$[*].userId", hasItem(dev.userId().intValue())));

    // străinul nu vede membrii/workspace -> 404
    mvc.perform(
            get("/api/projects/" + projectId + "/members")
                .header("Authorization", "Bearer " + stranger.token()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/projects/" + projectId + "/workspace")
                .header("Authorization", "Bearer " + stranger.token()))
        .andExpect(status().isNotFound());

    // workspace ca membru: proiect + membri + sarcini goale + fără invitații pending
    mvc.perform(
            get("/api/projects/" + projectId + "/workspace")
                .header("Authorization", "Bearer " + dev.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.id").value((int) projectId))
        .andExpect(jsonPath("$.members.length()", is(2)))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.pendingInvitations.length()", is(0)));
  }

  @Test
  void tasksCrudWithAccessChecks() throws Exception {
    Account owner = signup();
    Account dev = signup();
    Account stranger = signup();
    long projectId = createProject(owner.token(), "[\"backend\"]", "[\"Spring\"]");
    long invitationId = invite(owner.token(), projectId, dev.userId());
    accept(dev.token(), invitationId);

    // membrul creează sarcină asignată proprietarului
    var created =
        mvc.perform(
                post("/api/projects/" + projectId + "/tasks")
                    .header("Authorization", "Bearer " + dev.token())
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"API plati\",\"description\":\"Stripe integration\",\"assigneeId\":"
                            + owner.userId()
                            + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("todo"))
            .andExpect(jsonPath("$.assigneeId").value(owner.userId().intValue()))
            .andReturn();
    long taskId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

    // asignare unui străin (non-membru) -> 400
    mvc.perform(
            post("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"title\":\"Altele\",\"assigneeId\":" + stranger.userId() + "}"))
        .andExpect(status().isBadRequest());

    // străinul nu poate crea/citi -> 404
    mvc.perform(
            post("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + stranger.token())
                .contentType("application/json")
                .content("{\"title\":\"X\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + stranger.token()))
        .andExpect(status().isNotFound());

    // membrul actualizează statusul
    mvc.perform(
            patch("/api/projects/" + projectId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"status\":\"in-progress\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in-progress"));

    // filtru status
    mvc.perform(
            get("/api/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + owner.token())
                .param("status", "in-progress"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem((int) taskId)));

    // membrul (non-owner) nu poate șterge -> 403; proprietarul poate -> 204
    mvc.perform(
            delete("/api/projects/" + projectId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + dev.token()))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/projects/" + projectId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isNoContent());
  }

  @Test
  void repositoryUrlAndWorkspaceSeesIt() throws Exception {
    Account owner = signup();
    Account dev = signup();
    long projectId = createProject(owner.token(), "[\"backend\"]", "[\"Spring\"]");
    long invitationId = invite(owner.token(), projectId, dev.userId());
    accept(dev.token(), invitationId);

    // proprietarul setează repository-ul
    mvc.perform(
            patch("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/org/shop\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/org/shop"));

    // URL invalid -> 400; membrul nu poate edita proiectul -> 404
    mvc.perform(
            patch("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"repositoryUrl\":\"not-a-url\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"title\":\"Hacked\"}"))
        .andExpect(status().isNotFound());

    // workspace-ul vede repository-ul
    mvc.perform(
            get("/api/projects/" + projectId + "/workspace")
                .header("Authorization", "Bearer " + dev.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.repositoryUrl").value("https://github.com/org/shop"));
  }
}
