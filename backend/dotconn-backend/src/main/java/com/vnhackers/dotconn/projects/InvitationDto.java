// DTO de răspuns pentru o invitație (inclusiv calitatea de membru după acceptare).
package com.vnhackers.dotconn.projects;

import java.time.Instant;

public record InvitationDto(
    Long id,
    Long projectId,
    String projectTitle,
    Long inviterId,
    Long inviteeId,
    InvitationStatus status,
    Instant createdAt,
    Instant respondedAt) {
  public static InvitationDto from(Invitation invitation) {
    return new InvitationDto(
        invitation.getId(),
        invitation.getProject().getId(),
        invitation.getProject().getTitle(),
        invitation.getInviter().getId(),
        invitation.getInvitee().getId(),
        invitation.getStatus(),
        invitation.getCreatedAt(),
        invitation.getRespondedAt());
  }
}
