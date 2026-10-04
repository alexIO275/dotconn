// Teste de integrare pentru fluxul complet al API-ului de profiluri de programatori.
package com.vnhackers.dotconn.developers;

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
class DeveloperIntegrationTests {

  @Autowired WebApplicationContext context;
  private MockMvc mvc;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private String signupAndGetToken() throws Exception {
    String email = "dev-" + UUID.randomUUID() + "@example.com";
    String body =
        mapper.writeValueAsString(java.util.Map.of("email", email, "password", "Test-password-123"));
    var result =
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
  }

  @Test
  void patchListAndGetDeveloper() throws Exception {
    String token = signupAndGetToken();

    String patch =
        """
        {"displayName":"Ana Pop","description":"Full-stack dev","role":"full-stack",
         "technologies":["React","Spring"],"experienceYears":4,
         "githubUrl":"https://github.com/anapop","availability":"available","hourlyRate":50}
        """;
    mvc.perform(
            patch("/api/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(patch))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Ana Pop"))
        .andExpect(jsonPath("$.role").value("full-stack"));

    mvc.perform(get("/api/developers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());

    mvc.perform(
            get("/api/developers").header("Authorization", "Bearer " + token).param("role", "full-stack"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());

    mvc.perform(
            get("/api/developers").header("Authorization", "Bearer " + token).param("tech", "react"))
        .andExpect(status().isOk());

    // invalid role -> 400
    mvc.perform(
            get("/api/developers").header("Authorization", "Bearer " + token).param("role", "nope"))
        .andExpect(status().isBadRequest());

    // anonymous -> 401
    mvc.perform(get("/api/developers")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidPatch() throws Exception {
    String token = signupAndGetToken();
    mvc.perform(
            patch("/api/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"githubUrl\":\"not-a-url\",\"experienceYears\":99}"))
        .andExpect(status().isBadRequest());
  }
}
