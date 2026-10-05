package com.vnhackers.dotconn.projects;

import java.util.List;

public record TeamInvitationResult(List<InvitationDto> invitations, int alreadyMembers) {}
