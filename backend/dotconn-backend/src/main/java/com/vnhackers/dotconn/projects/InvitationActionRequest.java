// DTO validat pentru răspunsul la o invitație (doar invitatul o poate accepta sau refuza).
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvitationActionRequest(
    @NotBlank(message = "Lipsește acțiunea.")
        @Pattern(regexp = "accept|decline", message = "Acțiune invalidă. Folosește accept sau decline.")
        String action) {

  public InvitationStatus toStatus() {
    return "accept".equalsIgnoreCase(action.trim()) ? InvitationStatus.ACCEPTED : InvitationStatus.DECLINED;
  }
}
