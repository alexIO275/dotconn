package com.vnhackers.dotconn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.vnhackers.dotconn.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="GROQ_API_KEY=")
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private MockMvc mvc;
    private final JsonMapper mapper=JsonMapper.builder().build();
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private String newEmail(){return "test-"+UUID.randomUUID()+"@example.com";}
    private String body(String email){return mapper.writeValueAsString(java.util.Map.of("email",email,"password","Test-password-123"));}
    @Test void signupLoginAndProtectedAnalysis() throws Exception {
        String email=newEmail();
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body(email))).andExpect(status().isCreated()).andExpect(jsonPath("$.token").isNotEmpty());
        var user=users.findByEmail(email).orElseThrow();
        assertNotEquals("Test-password-123",user.getPasswordHash());
        assertTrue(encoder.matches("Test-password-123",user.getPasswordHash()));
        var login=mvc.perform(post("/api/auth/login").contentType("application/json").content(body(email))).andExpect(status().isOk()).andReturn();
        String token=mapper.readTree(login.getResponse().getContentAsString()).path("token").asString();
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/project-analysis").header("Authorization","Bearer "+token).contentType("application/json").content("{\"description\":\"Am frontend React și caut backend pentru rezervări.\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isNotEmpty());
    }
    @Test void rejectsAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(post("/api/project-analysis").contentType("application/json").content("{\"description\":\"Am nevoie de un backend pentru aplicația mea.\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer invalid-token")).andExpect(status().isUnauthorized());
    }
    @Test void rejectsDuplicateAccountsAndWrongPasswords() throws Exception {
        String email=newEmail();
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body(email))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(body(email))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email",email,"password","wrong")))).andExpect(status().isUnauthorized());
    }
    @Test void rejectsInvalidSignup() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{\"email\":\"not-email\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
    }
}
