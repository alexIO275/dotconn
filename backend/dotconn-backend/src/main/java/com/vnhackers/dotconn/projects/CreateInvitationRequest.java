// DTO validat pentru trimiterea unei invitații (proprietarul invită un programator).
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInvitationRequest(
    @NotNull(message = "Lipsește id-ul programatorului invitat.")
        @Positive(message = "Id-ul programatorului invitat este invalid.")
        Long inviteeId) {}
