package com.vnhackers.dotconn.analysis;
import jakarta.validation.Valid;
import com.vnhackers.dotconn.billing.BillingUsageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/project-analysis")
public class ProjectAnalysisController {
    private final ProjectAnalysisService service;
    private final BillingUsageService usage;
    public ProjectAnalysisController(ProjectAnalysisService service, BillingUsageService usage) {this.service=service;this.usage=usage;}
    @PostMapping
    public ProjectAnalysisResponse analyze(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectAnalysisRequest request) {
        Long userId;
        try { userId=Long.valueOf(jwt.getSubject()); }
        catch (NullPointerException | NumberFormatException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă."); }
        return usage.successfulAnalysis(userId, () -> service.analyze(request.description()));
    }
}
