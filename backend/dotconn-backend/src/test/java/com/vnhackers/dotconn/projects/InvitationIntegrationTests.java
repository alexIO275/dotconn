// Teste de integrare pentru invitații și echipe (acces pe roluri + tranziții de status).
package com.vnhackers.dotconn.projects;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vnhackers.dotconn.user.User;
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
class InvitationIntegrationTests {

  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private record Account(String token, Long userId) {}

  private Account signup() throws Exception {
    String email = "inv-" + UUID.randomUUID() + "@example.com";
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

  private long createProject(String token) throws Exception {
    String body =
        """
        {"title":"Platforma %s","description":"Am nevoie de backend pentru plati online si notificari.",
         "roles":["backend"],"tasks":["API plati"],"existingStack":["React"],
         "requiredTechnologies":["Spring"]}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 8));
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
            .andExpect(jsonPath("$.status").value("pending"))
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  @Test
  void inviteAcceptFlow() throws Exception {
    Account owner = signup();
    Account dev = signup();
    long projectId = createProject(owner.token());

    long invitationId = invite(owner.token(), projectId, dev.userId());

    // invitatul își vede invitația
    mvc.perform(get("/api/me/invitations").header("Authorization", "Bearer " + dev.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem((int) invitationId)));

    // proprietarul nu o vede în lista proprie (sunt doar invitațiile primite)
    mvc.perform(get("/api/me/invitations").header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", not(hasItem((int) invitationId))));

    // accept
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"action\":\"accept\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));

    // a doua procesare -> 409
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"action\":\"decline\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void declineFlowAndStatusFilter() throws Exception {
    Account owner = signup();
    Account dev = signup();
    long projectId = createProject(owner.token());
    long invitationId = invite(owner.token(), projectId, dev.userId());

    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"action\":\"decline\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("declined"));

    mvc.perform(
            get("/api/me/invitations")
                .header("Authorization", "Bearer " + dev.token())
                .param("status", "declined"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem((int) invitationId)));

    mvc.perform(
            get("/api/me/invitations")
                .header("Authorization", "Bearer " + dev.token())
                .param("status", "pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", not(hasItem((int) invitationId))));
  }

  @Test
  void accessChecks() throws Exception {
    Account owner = signup();
    Account dev = signup();
    Account stranger = signup();
    long projectId = createProject(owner.token());
    long invitationId = invite(owner.token(), projectId, dev.userId());

    // străinul nu poate invita în proiectul altcuiva -> 404
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .header("Authorization", "Bearer " + stranger.token())
                .contentType("application/json")
                .content("{\"inviteeId\":" + dev.userId() + "}"))
        .andExpect(status().isNotFound());

    // străinul nu poate răspunde la invitația altcuiva -> 404 (nu divulgăm existența)
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + stranger.token())
                .contentType("application/json")
                .content("{\"action\":\"accept\"}"))
        .andExpect(status().isNotFound());

    // proprietarul nu-și poate răspunde singur la invitație (nu e invitatul) -> 404
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"action\":\"accept\"}"))
        .andExpect(status().isNotFound());

    // auto-invitație -> 400
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"inviteeId\":" + owner.userId() + "}"))
        .andExpect(status().isBadRequest());

    // invitat inexistent -> 404
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"inviteeId\":999999999}"))
        .andExpect(status().isNotFound());

    // duplicat cât e în așteptare -> 409
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .header("Authorization", "Bearer " + owner.token())
                .contentType("application/json")
                .content("{\"inviteeId\":" + dev.userId() + "}"))
        .andExpect(status().isConflict());

    // acțiune invalidă -> 400
    mvc.perform(
            patch("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + dev.token())
                .contentType("application/json")
                .content("{\"action\":\"maybe\"}"))
        .andExpect(status().isBadRequest());

    // status filtru invalid -> 400
    mvc.perform(
            get("/api/me/invitations")
                .header("Authorization", "Bearer " + dev.token())
                .param("status", "nope"))
        .andExpect(status().isBadRequest());

    // anonim -> 401
    mvc.perform(get("/api/me/invitations")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .contentType("application/json")
                .content("{\"inviteeId\":" + dev.userId() + "}"))
        .andExpect(status().isUnauthorized());
  }
}
