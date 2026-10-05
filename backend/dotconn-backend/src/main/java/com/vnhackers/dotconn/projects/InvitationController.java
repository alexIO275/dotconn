// Controller pentru invitații: proprietarul invită, invitatul acceptă/refuză; fiecare vede doar ce-i aparține.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class InvitationController {
  private final ProjectRepository projects;
  private final InvitationRepository invitations;
  private final UserRepository users;

  public InvitationController(
      ProjectRepository projects, InvitationRepository invitations, UserRepository users) {
    this.projects = projects;
    this.invitations = invitations;
    this.users = users;
  }

  // Doar proprietarul proiectului poate invita.
  @Transactional
  @PostMapping("/api/projects/{id}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  public InvitationDto invite(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody CreateInvitationRequest req) {
    Long ownerId = currentUserId(jwt);
    Project project = projects.findByIdForUpdate(id)
        .filter(p -> p.getOwner().getId().equals(ownerId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));

    User invitee =
        users
            .findById(req.inviteeId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Utilizatorul invitat nu a fost găsit."));

    if (invitee.getId().equals(ownerId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nu te poți invita singur.");
    }
    var existing = invitations.findByProjectIdAndInviteeId(id, invitee.getId());
    if (existing.isPresent()) {
      String message =
          switch (existing.get().getStatus()) {
            case PENDING -> "Există deja o invitație în așteptare pentru acest programator.";
            case ACCEPTED -> "Programatorul este deja membru al proiectului.";
            case DECLINED -> "Invitația a fost deja refuzată.";
          };
      throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    User inviter = project.getOwner();
    return InvitationDto.from(invitations.save(new Invitation(project, inviter, invitee)));
  }

  // Fiecare utilizator își vede doar propriile invitații primite.
  @GetMapping("/api/me/invitations")
  public Page<InvitationDto> mine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    Long userId = currentUserId(jwt);
    if (status == null || status.isBlank()) {
      return invitations.findByInviteeId(userId, pageable).map(InvitationDto::from);
    }
    InvitationStatus filter;
    try {
      filter = InvitationStatus.fromSlug(status);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    return invitations.findByInviteeIdAndStatus(userId, filter, pageable).map(InvitationDto::from);
  }

  // Doar invitatul își poate accepta/refuza invitația, și doar cât e în așteptare.
  @Transactional
  @PatchMapping("/api/invitations/{id}")
  public InvitationDto respond(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody InvitationActionRequest req) {
    Long userId = currentUserId(jwt);
    Long projectId = invitations.findProjectIdForInvitee(id, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitația nu a fost găsită."));
    projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
    Invitation invitation = invitations.findForResponse(id, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitația nu a fost găsită."));
    if (invitation.getStatus() != InvitationStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Invitația a fost deja procesată.");
    }
    invitation.setStatus(req.toStatus());
    invitation.setRespondedAt(Instant.now());
    return InvitationDto.from(invitations.save(invitation));
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }
}
