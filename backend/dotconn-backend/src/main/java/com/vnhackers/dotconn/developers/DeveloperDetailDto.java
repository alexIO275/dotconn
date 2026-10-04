package com.vnhackers.dotconn.developers;

import java.math.BigDecimal;
import java.util.List;

public record DeveloperDetailDto(
    Long id,
    String displayName,
    String description,
    DeveloperRole role,
    List<String> technologies,
    Integer experienceYears,
    String githubUrl,
    Availability availability,
    BigDecimal hourlyRate) {

  public static DeveloperDetailDto from(DeveloperProfile profile) {
    return new DeveloperDetailDto(
        profile.getId(),
        profile.getDisplayName(),
        profile.getDescription(),
        profile.getRole(),
        List.copyOf(profile.getTechnologies()),
        profile.getExperienceYears(),
        profile.getGithubUrl(),
        profile.getAvailability(),
        profile.getHourlyRate());
  }
}
