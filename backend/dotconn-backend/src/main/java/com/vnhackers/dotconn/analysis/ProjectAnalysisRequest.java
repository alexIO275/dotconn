package com.vnhackers.dotconn.analysis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ProjectAnalysisRequest(@NotBlank @Size(min=20,max=5000) String description) {}
