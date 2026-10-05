package com.vnhackers.dotconn.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.user.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "GROQ_API_KEY=")
@ActiveProfiles("test")
class TeamProposalIntegrationTests {
  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  @Autowired DeveloperProfileRepository profiles;
  @Autowired InvitationRepository invitations;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private record Account(String token, Long id) {}

  @BeforeEach void setup() {
    profiles.deleteAll(); // Isolated in-memory test datasource only.
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private Account signup() throws Exception {
    String email = "package-" + UUID.randomUUID() + "@example.com";
    var response = mvc.perform(post("/api/auth/signup").contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Test-password-123"))))
        .andExpect(status().isCreated()).andReturn();
    return new Account(mapper.readTree(response.getResponse().getContentAsString()).path("token").asString(),
        users.findByEmail(email).orElseThrow().getId());
  }

  private Account developer(String role, String availability, int rate) throws Exception {
    Account account = signup();
    mvc.perform(patch("/api/me/profile").header("Authorization", "Bearer " + account.token())
        .contentType("application/json").content(mapper.writeValueAsString(Map.of(
            "displayName", "Dev " + role, "role", role, "technologies", List.of("React", "Spring"),
            "availability", availability, "hourlyRate", rate))))
        .andExpect(status().isOk());
    return account;
  }

  private long project(Account owner, List<String> roles, int cap) throws Exception {
    var response = mvc.perform(post("/api/projects").header("Authorization", "Bearer " + owner.token())
        .contentType("application/json").content(mapper.writeValueAsString(Map.of(
            "title", "Complete team", "description", "Platforma de colaborare frontend si backend pentru lansare.",
            "roles", roles, "tasks", List.of("Build API"), "requiredTechnologies", List.of("React", "Spring"),
            "maxHourlyRate", cap))))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value(owner.id().intValue())).andReturn();
    return mapper.readTree(response.getResponse().getContentAsString()).path("id").asLong();
  }

  private JsonNode proposals(Account owner, long project) throws Exception {
    var response = mvc.perform(get("/api/projects/" + project + "/team-proposals")
        .header("Authorization", "Bearer " + owner.token())).andExpect(status().isOk()).andReturn();
    return mapper.readTree(response.getResponse().getContentAsString());
  }

  private String batch(JsonNode team) {
    List<Map<String, Object>> assignments = new ArrayList<>();
    for (JsonNode member : team.path("members")) {
      assignments.add(Map.of("developerId", member.path("developerId").asLong(), "role", member.path("role").asString()));
    }
    return mapper.writeValueAsString(Map.of("assignments", assignments));
  }

  private JsonNode invite(Account owner, long project, String payload) throws Exception {
    var response = mvc.perform(post("/api/projects/" + project + "/team-invitations")
        .header("Authorization", "Bearer " + owner.token()).contentType("application/json").content(payload))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(response.getResponse().getContentAsString());
  }

  @Test void jointRoleAssignmentAvoidsGreedyFullStackFailure() throws Exception {
    Account owner = signup();
    Account fullstack = developer("full-stack", "available", 40);
    Account frontend = developer("frontend", "available", 40);
    long project = project(owner, List.of("frontend", "backend"), 50);
    JsonNode proposal = proposals(owner, project);
    assertThat(proposal.path("status").asString()).isEqualTo("ready");
    assertThat(proposal.path("teams").size()).isEqualTo(1);
    JsonNode members = proposal.path("teams").get(0).path("members");
    assertThat(members.get(0).path("developerId").asLong()).isEqualTo(frontend.id());
    assertThat(members.get(1).path("developerId").asLong()).isEqualTo(fullstack.id());
    assertThat(members.get(1).path("role").asString()).isEqualTo("backend");
  }

  @Test void oneFullstackCannotFillTwoRolesAndNoPartialInvites() throws Exception {
    Account owner = signup();
    Account fullstack = developer("full-stack", "available", 40);
    long project = project(owner, List.of("frontend", "backend"), 50);
    var proposal = proposals(owner, project);
    assertThat(proposal.path("status").asString()).isEqualTo("unavailable");
    assertThat(proposal.path("missingRoles").size()).isEqualTo(1);
    assertThat(proposal.path("teams").size()).isZero();
    String duplicate = mapper.writeValueAsString(Map.of("assignments", List.of(
        Map.of("role", "frontend", "developerId", fullstack.id()),
        Map.of("role", "backend", "developerId", fullstack.id()))));
    mvc.perform(post("/api/projects/" + project + "/team-invitations")
        .header("Authorization", "Bearer " + owner.token()).contentType("application/json").content(duplicate))
        .andExpect(status().isConflict());
    assertThat(invitations.findByProjectId(project)).isEmpty();
    mvc.perform(post("/api/projects/" + project + "/auto-assemble")
        .header("Authorization", "Bearer " + owner.token()).contentType("application/json").content("{}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(0));
    assertThat(invitations.findByProjectId(project)).isEmpty();
  }

  @Test void alternativesAreDistinctPersonSetsAndExcludeUnavailableOrExpensive() throws Exception {
    Account owner = developer("frontend", "available", 0);
    Account f1 = developer("frontend", "available", 35);
    Account f2 = developer("frontend", "available", 40);
    Account b1 = developer("backend", "available", 35);
    Account b2 = developer("backend", "partially-available", 40);
    Account unavailable = developer("full-stack", "unavailable", 20);
    Account expensive = developer("full-stack", "available", 500);
    long project = project(owner, List.of("frontend", "backend"), 50);
    JsonNode proposal = proposals(owner, project);
    assertThat(proposal.path("teams").size()).isEqualTo(3);
    var personSets = new HashSet<HashSet<Long>>();
    for (JsonNode team : proposal.path("teams")) {
      var ids = new HashSet<Long>();
      for (JsonNode member : team.path("members")) ids.add(member.path("developerId").asLong());
      assertThat(ids).hasSize(2).doesNotContain(owner.id(), unavailable.id(), expensive.id());
      assertThat(personSets.add(ids)).isTrue();
    }
  }

  @Test void changedEligibilityRejectsWholeBatchWithoutPartialWrites() throws Exception {
    Account owner = signup();
    developer("frontend", "available", 40);
    Account backend = developer("backend", "available", 40);
    long project = project(owner, List.of("frontend", "backend"), 50);
    String payload = batch(proposals(owner, project).path("teams").get(0));
    mvc.perform(patch("/api/me/profile").header("Authorization", "Bearer " + backend.token())
        .contentType("application/json").content("{\"availability\":\"unavailable\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/projects/" + project + "/team-invitations")
        .header("Authorization", "Bearer " + owner.token()).contentType("application/json").content(payload))
        .andExpect(status().isConflict());
    assertThat(invitations.findByProjectId(project)).isEmpty();
  }

  @Test void batchRetryAndDeclinedReinviteKeepOneInvitationPerPerson() throws Exception {
    Account owner = signup();
    Account frontend = developer("frontend", "available", 40);
    Account backend = developer("backend", "available", 40);
    long project = project(owner, List.of("frontend", "backend"), 50);
    String payload = batch(proposals(owner, project).path("teams").get(0));
    JsonNode first = invite(owner, project, payload);
    assertThat(first.path("invitations").size()).isEqualTo(2);
    JsonNode repeated = invite(owner, project, payload);
    assertThat(repeated.path("invitations").get(0).path("id").asLong())
        .isEqualTo(first.path("invitations").get(0).path("id").asLong());
    var invitation = invitations.findByProjectIdAndInviteeId(project, frontend.id()).orElseThrow();
    mvc.perform(patch("/api/invitations/" + invitation.getId()).header("Authorization", "Bearer " + frontend.token())
        .contentType("application/json").content("{\"action\":\"decline\"}"))
        .andExpect(status().isOk());
    invite(owner, project, payload);
    assertThat(invitations.findByProjectId(project)).hasSize(2);
    assertThat(invitations.findByProjectIdAndInviteeId(project, frontend.id()).orElseThrow().getStatus())
        .isEqualTo(InvitationStatus.PENDING);
    assertThat(invitations.findByProjectIdAndInviteeId(project, backend.id()).orElseThrow().getAssignedRole())
        .isEqualTo("backend");
  }

  @Test void acceptedMembersCompleteTeamAndSeeProjectWorkspace() throws Exception {
    Account owner = signup();
    Account frontend = developer("frontend", "available", 40);
    Account backend = developer("backend", "available", 40);
    Account stranger = signup();
    long project = project(owner, List.of("frontend", "backend"), 50);
    invite(owner, project, batch(proposals(owner, project).path("teams").get(0)));
    for (Account dev : List.of(frontend, backend)) {
      long id = invitations.findByProjectIdAndInviteeId(project, dev.id()).orElseThrow().getId();
      mvc.perform(get("/api/projects/" + project + "/workspace").header("Authorization", "Bearer " + dev.token()))
          .andExpect(status().isNotFound());
      mvc.perform(patch("/api/invitations/" + id).header("Authorization", "Bearer " + dev.token())
          .contentType("application/json").content("{\"action\":\"accept\"}"))
          .andExpect(status().isOk());
    }
    assertThat(proposals(owner, project).path("status").asString()).isEqualTo("complete");
    mvc.perform(get("/api/projects").header("Authorization", "Bearer " + frontend.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem((int) project)));
    mvc.perform(get("/api/projects/" + project).header("Authorization", "Bearer " + backend.token()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/projects/" + project + "/workspace").header("Authorization", "Bearer " + frontend.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(3));
    mvc.perform(get("/api/projects/" + project + "/team-proposals").header("Authorization", "Bearer " + frontend.token()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/projects/" + project + "/workspace").header("Authorization", "Bearer " + stranger.token()))
        .andExpect(status().isNotFound());
  }

  @Test void apiContractAndClearAssigneePersistForMembers() throws Exception {
    Account owner = signup();
    long project = project(owner, List.of("backend"), 50);
    mvc.perform(patch("/api/projects/" + project).header("Authorization", "Bearer " + owner.token())
        .contentType("application/json").content("{\"apiContract\":\"GET /api/products -> Product[]\",\"repositoryUrl\":\"https://github.com/org/demo\",\"clearMaxHourlyRate\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.apiContract").value("GET /api/products -> Product[]"))
        .andExpect(jsonPath("$.maxHourlyRate").isEmpty());
    var created = mvc.perform(post("/api/projects/" + project + "/tasks").header("Authorization", "Bearer " + owner.token())
        .contentType("application/json").content("{\"title\":\"Implement API\",\"assigneeId\":" + owner.id() + "}"))
        .andExpect(status().isCreated()).andReturn();
    long taskId = mapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(patch("/api/projects/" + project + "/tasks/" + taskId).header("Authorization", "Bearer " + owner.token())
        .contentType("application/json").content("{\"clearAssignee\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeId").isEmpty());
    mvc.perform(get("/api/projects/" + project + "/workspace").header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.project.apiContract").value("GET /api/products -> Product[]"));
  }

  @Test void concurrentBatchInvitesAreIdempotent() throws Exception {
    Account owner = signup();
    developer("frontend", "available", 40);
    developer("backend", "available", 40);
    long project = project(owner, List.of("frontend", "backend"), 50);
    String payload = batch(proposals(owner, project).path("teams").get(0));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> invite(owner, project, payload));
      var second = executor.submit(() -> invite(owner, project, payload));
      assertThat(first.get().path("invitations").size()).isEqualTo(2);
      assertThat(second.get().path("invitations").size()).isEqualTo(2);
    }
    assertThat(invitations.findByProjectId(project)).hasSize(2);
  }
}
