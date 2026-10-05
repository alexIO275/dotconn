package com.vnhackers.dotconn.analysis;

import org.junit.jupiter.api.Test;
import com.vnhackers.dotconn.billing.BillingUsageService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.mockito.Mockito.*;
import java.util.function.Supplier;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.net.URI;
import java.net.http.HttpClient;

class ProjectAnalysisControllerTests {
    @Test void rejectsInvalidDescriptionBeforeCallingProvider() throws Exception {
        try(var validator=new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var service=new ProjectAnalysisService(new GroqAnalysisClient("","test",URI.create("http://127.0.0.1:1"),HttpClient.newHttpClient()),validator);
            var usage=mock(BillingUsageService.class);
            when(usage.successfulAnalysis(anyLong(), any())).thenAnswer(call -> ((Supplier<?>)call.getArgument(1)).get());
            var jwt=Jwt.withTokenValue("unit-test").header("alg","none").subject("1").build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            var mvc=MockMvcBuilders.standaloneSetup(new ProjectAnalysisController(service, usage))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new AnalysisErrorHandler()).setValidator(validator).build();
            mvc.perform(post("/api/project-analysis").contentType("application/json").content("{\"description\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
            mvc.perform(post("/api/project-analysis").contentType("application/json").content("{\"description\":\"Am frontend în React și caut backend pentru rezervări.\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GROQ_API_KEY")));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
