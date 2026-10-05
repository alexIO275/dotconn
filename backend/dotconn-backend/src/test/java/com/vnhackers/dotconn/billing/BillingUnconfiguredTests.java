package com.vnhackers.dotconn.billing;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
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

@SpringBootTest(properties={"app.billing.secret-key=", "app.billing.webhook-secret=",
    "app.billing.price-bronze=", "app.billing.price-silver=", "app.billing.price-gold=", "GROQ_API_KEY="})
@ActiveProfiles("test")
class BillingUnconfiguredTests {
  @Autowired WebApplicationContext context;
  private MockMvc mvc;
  @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
  @Test void plansStayPublicAndMissingConfigurationDoesNotActivateOrAttemptCheckout() throws Exception {
    mvc.perform(get("/api/billing/plans")).andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("unconfigured")).andExpect(jsonPath("$.plans.length()").value(4));
    var mapper=JsonMapper.builder().build();
    var signup=mvc.perform(post("/api/auth/signup").contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("email","unconfigured-"+UUID.randomUUID()+"@example.com","password","Test-password-123"))))
        .andExpect(status().isCreated()).andReturn();
    String token=mapper.readTree(signup.getResponse().getContentAsString()).path("token").asString();
    mvc.perform(post("/api/billing/checkout").header("Authorization","Bearer "+token)
        .contentType("application/json").content("{\"plan\":\"silver\"}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").isNotEmpty());
    mvc.perform(get("/api/billing/subscription").header("Authorization","Bearer "+token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("free")).andExpect(jsonPath("$.monthlyAnalysisLimit").value(3));
  }
}
