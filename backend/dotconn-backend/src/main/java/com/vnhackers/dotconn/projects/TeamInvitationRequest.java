package com.vnhackers.dotconn.projects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TeamInvitationRequest(
    @NotNull @Size(min = 1, max = 8) List<@Valid Assignment> assignments) {
  public record Assignment(@NotBlank String role, @NotNull @Positive Long developerId) {}
}
