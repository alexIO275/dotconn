// Asamblează automat echipa: invită cei mai buni candidați din recomandări, acoperind rolurile cerute.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TeamAssemblyService {
  private final RecommendationService recommendations;
  private final InvitationRepository invitations;
  private final UserRepository users;

  public TeamAssemblyService(
      RecommendationService recommendations,
      InvitationRepository invitations,
      UserRepository users) {
    this.recommendations = recommendations;
    this.invitations = invitations;
    this.users = users;
  }

  // Invită automat până la maxInvitations candidați cu scor >= minScore: mai întâi câte unul
  // pe fiecare rol cerut (cel mai bun pe rol), apoi completarea după scor. Sare peste proprietar
  // și peste cei deja invitați/membri. Întoarce lista goală dacă nu există candidați.
  public List<InvitationDto> autoAssemble(Project project, int maxInvitations, int minScore) {
    int safeMax = Math.min(Math.max(maxInvitations, 1), 20);
    int safeMin = Math.min(Math.max(minScore, 0), 100);
    Long ownerId = project.getOwner().getId();

    Set<Long> alreadyInvited =
        invitations.findByProjectId(project.getId()).stream()
            .map(inv -> inv.getInvitee().getId())
            .collect(Collectors.toSet());

    List<RecommendationDto> candidates =
        recommendations.recommend(project, 50).stream()
            .filter(rec -> rec.score() >= safeMin)
            .filter(rec -> !rec.developerId().equals(ownerId))
            .filter(rec -> !alreadyInvited.contains(rec.developerId()))
            .toList();

    Set<String> wantedRoles =
        project.getRoles().stream()
            .map(r -> r.trim().toLowerCase())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    List<RecommendationDto> picked = new ArrayList<>();
    Set<Long> pickedIds = new LinkedHashSet<>();
    // Pasul 1: câte un candidat (cel mai bun) pentru fiecare rol cerut.
    for (String role : wantedRoles) {
      if (picked.size() >= safeMax) {
        break;
      }
      candidates.stream()
          .filter(rec -> !pickedIds.contains(rec.developerId()))
          .filter(rec -> coversRole(rec, role))
          .findFirst()
          .ifPresent(
              rec -> {
                picked.add(rec);
                pickedIds.add(rec.developerId());
              });
    }
    // Pasul 2: completare după scor până la limită.
    for (RecommendationDto rec : candidates) {
      if (picked.size() >= safeMax) {
        break;
      }
      if (pickedIds.add(rec.developerId())) {
        picked.add(rec);
      }
    }

    List<InvitationDto> created = new ArrayList<>();
    for (RecommendationDto rec : picked) {
      User invitee = users.findById(rec.developerId()).orElse(null);
      if (invitee == null || invitee.getId().equals(ownerId)) {
        continue;
      }
      if (invitations.findByProjectIdAndInviteeId(project.getId(), invitee.getId()).isPresent()) {
        continue;
      }
      created.add(
          InvitationDto.from(invitations.save(new Invitation(project, project.getOwner(), invitee))));
    }
    return created;
  }

  private static boolean coversRole(RecommendationDto rec, String wantedRole) {
    if (rec.role() == null) {
      return false;
    }
    String slug = rec.role().slug().toLowerCase();
    if (slug.equals(wantedRole)) {
      return true;
    }
    // Full-stack acoperă cereri de frontend/backend.
    return rec.role().name().equals("FULL_STACK")
        && (wantedRole.equals("frontend") || wantedRole.equals("backend"));
  }
}
