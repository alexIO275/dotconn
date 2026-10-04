// DTO pentru workspace-ul comun: proiect + membri + sarcini (+ invitații în așteptare, doar pentru proprietar).
package com.vnhackers.dotconn.projects;

import java.util.List;

public record WorkspaceDto(
    ProjectDto project,
    List<MemberDto> members,
    List<TaskDto> tasks,
    List<InvitationDto> pendingInvitations) {}
