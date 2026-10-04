// Teste de integrare pentru proiecte și recomandări (izolare pe owner + scoring determinist).
package com.vnhackers.dotconn.projects;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProjectIntegrationTests {

  @Autowired WebApplicationContext context;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private String signupAndGetToken() throws Exception {
    String email = "proj-" + UUID.randomUUID() + "@example.com";
    String body =
        mapper.writeValueAsString(java.util.Map.of("email", email, "password", "Test-password-123"));
    var result =
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
  }

  private void seedProfile(String token, String role, String techsJson, String availability, String rate)
      throws Exception {
    String body =
        """
        {"displayName":"Dev %s","role":"%s","technologies":%s,
         "experienceYears":4,"githubUrl":"https://github.com/dev-%s",
         "availability":"%s","hourlyRate":%s}
        """
            .formatted(role, role, techsJson, UUID.randomUUID().toString().substring(0, 8), availability, rate);
    mvc.perform(
            patch("/api/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
  }

  private long createProject(String token, String rolesJson, String techsJson, String maxRate)
      throws Exception {
    String body =
        """
        {"title":"Shop online %s","description":"Am frontend React si caut backend pentru rezervari cu plati online.",
         "summary":"Magazin cu rezervari","roles":%s,"tasks":["API rezervari"],
         "existingStack":["React"],"requiredTechnologies":%s,"maxHourlyRate":%s}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 8), rolesJson, techsJson, maxRate);
    var result =
        mvc.perform(
                post("/api/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  @Test
  void createAndReadProject() throws Exception {
    String token = signupAndGetToken();
    long id = createProject(token, "[\"backend\"]", "[\"Spring\"]", "60");

    mvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

    mvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.roles", hasItem("backend")));
  }

  @Test
  void rejectsInvalidProject() throws Exception {
    String token = signupAndGetToken();
    mvc.perform(
            post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"X\",\"description\":\"prea scurt\",\"roles\":[\"nope\"],\"tasks\":[]}"))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  @Test
  void isolatesProjectsBetweenUsers() throws Exception {
    String owner = signupAndGetToken();
    String stranger = signupAndGetToken();
    long id = createProject(owner, "[\"backend\"]", "[\"Spring\"]", "60");

    mvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + stranger))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/projects/" + id + "/recommendations").header("Authorization", "Bearer " + stranger))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/projects").header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", not(hasItem((int) id))));
  }

  @Test
  void recommendationsOrderedAndEmpty() throws Exception {
    String owner = signupAndGetToken();
    String strongToken = signupAndGetToken();
    String weakToken = signupAndGetToken();

    seedProfile(strongToken, "backend", "[\"Spring\",\"Postgres\"]", "available", "40");
    seedProfile(weakToken, "frontend", "[\"Vue\"]", "unavailable", "999");

    long id = createProject(owner, "[\"backend\"]", "[\"Spring\"]", "60");

    mvc.perform(
            get("/api/projects/" + id + "/recommendations")
                .header("Authorization", "Bearer " + owner)
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].score").isNumber())
        .andExpect(jsonPath("$[0].reasons").isArray())
        .andExpect(jsonPath("$[0].reasons.length()", greaterThanOrEqualTo(1)));

    long impossible = createProject(owner, "[\"security\"]", "[\"CobolMainframeXYZ\"]", "1");
    mvc.perform(
            get("/api/projects/" + impossible + "/recommendations")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()", is(0)));
  }
}
