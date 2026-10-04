package com.vnhackers.dotconn.analysis;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/project-analysis")
public class ProjectAnalysisController {
    private final ProjectAnalysisService service;
    public ProjectAnalysisController(ProjectAnalysisService service) {this.service=service;}
    @PostMapping
    public ProjectAnalysisResponse analyze(@Valid @RequestBody ProjectAnalysisRequest request) {return service.analyze(request.description());}
}
