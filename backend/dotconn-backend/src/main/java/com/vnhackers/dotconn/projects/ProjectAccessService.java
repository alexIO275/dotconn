// Serviciu central pentru verificări de acces: proprietar vs membru (invitație acceptată) vs străin.
package com.vnhackers.dotconn.projects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAccessService {
  private final ProjectRepository projects;
  private final InvitationRepository invitations;

  public ProjectAccessService(ProjectRepository projects, InvitationRepository invitations) {
    this.projects = projects;
    this.invitations = invitations;
  }

  // Doar proprietarul; străinii și membrii primesc 404 ca să nu se divulge existența.
  public Project requireOwner(Long projectId, Long userId) {
    return projects
        .findByIdAndOwnerId(projectId, userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
  }

  // Proprietarul sau un membru (invitație acceptată); restul primesc 404.
  public Project requireMember(Long projectId, Long userId) {
    var project =
        projects
            .findById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
    if (project.getOwner().getId().equals(userId)) {
      return project;
    }
    boolean member =
        invitations
            .findByProjectIdAndInviteeId(projectId, userId)
            .map(inv -> inv.getStatus() == InvitationStatus.ACCEPTED)
            .orElse(false);
    if (!member) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit.");
    }
    return project;
  }

  public boolean isOwnerOrMember(Long projectId, Long userId) {
    try {
      requireMember(projectId, userId);
      return true;
    } catch (ResponseStatusException e) {
      return false;
    }
  }
}
