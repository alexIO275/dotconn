// Calculează recomandări deterministe din profiluri reale (rol 40 + skills 30 + disponibilitate 20 + buget 10).
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperProfile;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.developers.DeveloperRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
  private final DeveloperProfileRepository profiles;

  public RecommendationService(DeveloperProfileRepository profiles) {
    this.profiles = profiles;
  }

  public List<RecommendationDto> recommend(Project project, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 20);
    Set<String> wantedRoles =
        project.getRoles().stream().map(r -> r.trim().toLowerCase()).collect(Collectors.toSet());
    Set<String> required =
        project.getRequiredTechnologies().stream()
            .map(t -> t.trim().toLowerCase())
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    List<RecommendationDto> scored = new ArrayList<>();
    for (DeveloperProfile profile : profiles.findAllWithTechnologies()) {
      if (profile.getRole() == null || profile.getId().equals(project.getOwner().getId())) continue;
      int rolePoints = rolePoints(profile.getRole(), wantedRoles);
      SkillMatch skills = skillPoints(profile.getTechnologies(), required);
      // Fără suprapunere pe rol și pe competențe, profilul nu e relevant —
      // disponibilitatea și bugetul singure nu sunt suficiente (altfel lista n-ar fi niciodată goală).
      if (rolePoints == 0 && skills.points() == 0) {
        continue;
      }
      int availabilityPoints = availabilityPoints(profile.getAvailability());
      int budgetPoints = budgetPoints(profile, project);

      int score = rolePoints + skills.points() + availabilityPoints + budgetPoints;
      if (score <= 0) {
        continue;
      }
      scored.add(
          new RecommendationDto(
              profile.getId(),
              profile.getDisplayName(),
              profile.getRole(),
              List.copyOf(profile.getTechnologies()),
              profile.getExperienceYears(),
              profile.getAvailability(),
              profile.getHourlyRate(),
              score,
              reasons(profile, project, wantedRoles, required, skills, rolePoints, availabilityPoints,
                  budgetPoints)));
    }
    scored.sort(Comparator.comparingInt(RecommendationDto::score).reversed());
    return scored.stream().limit(safeLimit).toList();
  }

  private static int rolePoints(DeveloperRole role, Set<String> wantedRoles) {
    if (role == null || wantedRoles.isEmpty()) {
      return 0;
    }
    String slug = role.slug().toLowerCase();
    if (wantedRoles.contains(slug)) {
      return 40;
    }
    // FULL_STACK acoperă cereri de frontend/backend și invers (parțial).
    if (role == DeveloperRole.FULL_STACK
        && (wantedRoles.contains("frontend") || wantedRoles.contains("backend"))) {
      return 40;
    }
    if ((slug.equals("frontend") || slug.equals("backend")) && wantedRoles.contains("full-stack")) {
      return 20;
    }
    return 0;
  }

  private record SkillMatch(int points, List<String> common) {}

  private static SkillMatch skillPoints(List<String> profileTechs, Set<String> required) {
    if (required.isEmpty()) {
      return new SkillMatch(15, List.of());
    }
    Set<String> profile =
        profileTechs.stream().map(t -> t.trim().toLowerCase()).collect(Collectors.toSet());
    List<String> common =
        required.stream().filter(profile::contains).sorted().toList();
    if (common.isEmpty()) {
      return new SkillMatch(0, List.of());
    }
    int points = (int) Math.round(30.0 * common.size() / required.size());
    return new SkillMatch(Math.max(points, 5), common);
  }

  private static int availabilityPoints(Availability availability) {
    if (availability == null) {
      return 0;
    }
    return switch (availability) {
      case AVAILABLE -> 20;
      case PARTIALLY_AVAILABLE -> 10;
      case UNAVAILABLE -> 0;
    };
  }

  private static int budgetPoints(DeveloperProfile profile, Project project) {
    if (project.getMaxHourlyRate() == null || profile.getHourlyRate() == null) {
      return 10;
    }
    return profile.getHourlyRate().compareTo(project.getMaxHourlyRate()) <= 0 ? 10 : 0;
  }

  private static List<String> reasons(
      DeveloperProfile profile,
      Project project,
      Set<String> wantedRoles,
      Set<String> required,
      SkillMatch skills,
      int rolePoints,
      int availabilityPoints,
      int budgetPoints) {
    List<String> reasons = new ArrayList<>();
    if (rolePoints > 0) {
      reasons.add(
          "Rol potrivit: "
              + (profile.getRole() == null ? "-" : profile.getRole().slug())
              + " (cerut: "
              + new TreeSet<>(wantedRoles).stream().sorted().collect(Collectors.joining(", "))
              + ").");
    }
    if (!required.isEmpty()) {
      if (skills.common().isEmpty()) {
        reasons.add("Fără competențe comune (cerut: " + String.join(", ", new TreeSet<>(required)) + ").");
      } else {
        reasons.add(
            "Competențe comune: "
                + String.join(", ", skills.common())
                + " ("
                + skills.common().size()
                + "/"
                + required.size()
                + ").");
      }
    } else {
      reasons.add("Fără tehnologii obligatorii specificate.");
    }
    if (availabilityPoints == 20) {
      reasons.add("Disponibil acum.");
    } else if (availabilityPoints == 10) {
      reasons.add("Parțial disponibil.");
    } else {
      reasons.add("Disponibilitate neconfirmată.");
    }
    if (project.getMaxHourlyRate() == null) {
      reasons.add("Fără limită de tarif specificată.");
    } else if (profile.getHourlyRate() == null) {
      reasons.add("Tarif neconfirmat; limita proiectului este " + project.getMaxHourlyRate() + " pe oră.");
    } else if (budgetPoints > 0) {
      reasons.add(
          "Tarif "
              + profile.getHourlyRate()
              + " ≤ buget "
              + project.getMaxHourlyRate()
              + ".");
    } else {
      reasons.add(
          "Tarif "
              + profile.getHourlyRate()
              + " peste bugetul "
              + project.getMaxHourlyRate()
              + ".");
    }
    return reasons;
  }
}
