// DTO de răspuns pentru un proiect (listă + detaliu).
package com.vnhackers.dotconn.projects;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProjectDto(
    Long id,
    String title,
    String description,
    String summary,
    List<String> roles,
    List<String> tasks,
    List<String> existingStack,
    List<String> requiredTechnologies,
    BigDecimal maxHourlyRate,
    String repositoryUrl,
    Instant createdAt) {
  public static ProjectDto from(Project p) {
    return new ProjectDto(
        p.getId(),
        p.getTitle(),
        p.getDescription(),
        p.getSummary(),
        List.copyOf(p.getRoles()),
        List.copyOf(p.getTasks()),
        List.copyOf(p.getExistingStack()),
        List.copyOf(p.getRequiredTechnologies()),
        p.getMaxHourlyRate(),
        p.getRepositoryUrl(),
        p.getCreatedAt());
  }
}
