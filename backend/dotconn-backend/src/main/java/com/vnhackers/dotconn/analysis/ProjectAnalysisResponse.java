package com.vnhackers.dotconn.analysis;
import jakarta.validation.constraints.*;
import java.util.List;
public record ProjectAnalysisResponse(
    @NotBlank @Size(max=1000) String summary,
    @NotNull @Size(max=8) List<@Pattern(regexp="frontend|backend|full-stack|mobile|devops|qa|data|security") String> roles,
    @NotNull @Size(max=20) List<@NotBlank @Size(max=500) String> tasks,
    @NotNull @Size(max=20) List<@NotBlank @Size(max=100) String> existingStack,
    @NotNull @Size(max=10) List<@NotBlank @Size(max=500) String> missingInformation) {}
