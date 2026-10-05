package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperRole;
import java.math.BigDecimal;
import java.util.List;

public record TeamProposalsDto(
    String status, List<String> requiredRoles, List<String> missingRoles,
    List<Team> teams, String message) {
  public record Team(String id, int score, List<Member> members) {}
  public record Member(Long developerId, String displayName, String role,
      DeveloperRole developerRole, List<String> technologies, Availability availability,
      BigDecimal hourlyRate, boolean existingMember, int score, List<String> reasons) {}
}
