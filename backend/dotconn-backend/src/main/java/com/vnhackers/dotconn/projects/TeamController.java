// Controller pentru echipă și workspace: membri, asamblare automată și vederea comună a proiectului.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TeamController {
  private final ProjectAccessService access;
  private final InvitationRepository invitations;
  private final ProjectTaskRepository tasks;
  private final DeveloperProfileRepository profiles;
  private final TeamAssemblyService assembly;

  public TeamController(
      ProjectAccessService access,
      InvitationRepository invitations,
      ProjectTaskRepository tasks,
      DeveloperProfileRepository profiles,
      TeamAssemblyService assembly) {
    this.access = access;
    this.invitations = invitations;
    this.tasks = tasks;
    this.profiles = profiles;
    this.assembly = assembly;
  }

  // Membrii echipei: proprietarul + programatorii cu invitație acceptată. Vizibil membrilor.
  @GetMapping("/api/projects/{id}/members")
  public List<MemberDto> members(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    Project project = access.requireMember(id, currentUserId(jwt));
    return listMembers(project);
  }

  // Workspace-ul comun: proiect + membri + sarcini (+ invitații în așteptare, doar pentru proprietar).
  @GetMapping("/api/projects/{id}/workspace")
  public WorkspaceDto workspace(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    Long userId = currentUserId(jwt);
    Project project = access.requireMember(id, userId);
    List<MemberDto> members = listMembers(project);
    List<TaskDto> projectTasks =
        tasks.findByProjectId(id, org.springframework.data.domain.Pageable.unpaged()).stream()
            .map(TaskDto::from)
            .toList();
    List<InvitationDto> pending =
        project.getOwner().getId().equals(userId)
            ? invitations.findByProjectIdAndStatus(id, InvitationStatus.PENDING).stream()
                .map(InvitationDto::from)
                .toList()
            : List.of();
    return new WorkspaceDto(ProjectDto.from(project), members, projectTasks, pending);
  }

  // Asamblează automat echipa: doar proprietarul poate invita în bloc din recomandări.
  @PostMapping("/api/projects/{id}/auto-assemble")
  @ResponseStatus(HttpStatus.CREATED)
  public List<InvitationDto> autoAssemble(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody(required = false) AutoAssembleRequest req) {
    Long userId = currentUserId(jwt);
    Project project = access.requireOwner(id, userId);
    int max = req == null || req.maxInvitations() == null ? 5 : req.maxInvitations();
    int minScore = req == null || req.minScore() == null ? 30 : req.minScore();
    return assembly.autoAssemble(project, max, minScore);
  }

  private List<MemberDto> listMembers(Project project) {
    List<MemberDto> members = new ArrayList<>();
    Long ownerId = project.getOwner().getId();
    var ownerProfile = profiles.findById(ownerId).orElse(null);
    members.add(
        new MemberDto(
            ownerId,
            ownerProfile == null ? null : ownerProfile.getDisplayName(),
            ownerProfile == null ? null : ownerProfile.getRole(),
            ownerProfile == null ? null : ownerProfile.getAvailability(),
            true,
            project.getCreatedAt()));
    invitations.findByProjectIdAndStatus(project.getId(), InvitationStatus.ACCEPTED).stream()
        .sorted(
            Comparator.comparing(
                inv -> inv.getRespondedAt() == null ? inv.getCreatedAt() : inv.getRespondedAt()))
        .forEach(
            inv -> {
              Long userId = inv.getInvitee().getId();
              var profile = profiles.findById(userId).orElse(null);
              members.add(
                  new MemberDto(
                      userId,
                      profile == null ? null : profile.getDisplayName(),
                      profile == null ? null : profile.getRole(),
                      profile == null ? null : profile.getAvailability(),
                      false,
                      inv.getRespondedAt() == null ? inv.getCreatedAt() : inv.getRespondedAt()));
            });
    return members;
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }
}
