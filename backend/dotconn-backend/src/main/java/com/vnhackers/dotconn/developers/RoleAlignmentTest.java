package com.vnhackers.dotconn.developers;

import com.vnhackers.dotconn.analysis.ProjectAnalysisResponse;
import jakarta.validation.Validation;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleAlignmentTest {
  @Test
  void profileRolesCoverAnalysisRoles() {
    Set<String> profileSlugs =
      Stream.of(DeveloperRole.values())..map(DeveloperRole::slug).collect(Collectors.toSet());

    Set<String> analysisRoles =
      Set.of("frontend", "backend", "full-stack", "mobile", "devops", "qa", "data", "security");
    assertTrue(profileSlugs.containsAll(analysisRoles),
      "Lipsesc roluri in DeveloperRole: " + analysisRoles.stream().filter(r -> !profileSlugs.contains(r)).
      toList());
  }
}
