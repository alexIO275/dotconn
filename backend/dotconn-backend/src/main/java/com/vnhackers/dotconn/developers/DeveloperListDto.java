// DTO compact returnat în lista paginată de programatori.
package com.vnhackers.dotconn.developers;

import java.math.BigDecimal;
import java.util.List;

public record DeveloperListDto(
    Long id,
    String displayName,
    DeveloperRole role,
    List<String> technologies,
    Integer experienceYears,
    Availability availability,
    BigDecimal hourlyRate) {

  public static DeveloperListDto from(DeveloperProfile profile) {
    return new DeveloperListDto(
        profile.getId(),
        profile.getDisplayName(),
        profile.getRole(),
        List.copyOf(profile.getTechnologies()),
        profile.getExperienceYears(),
        profile.getAvailability(),
        profile.getHourlyRate());
  }
}
