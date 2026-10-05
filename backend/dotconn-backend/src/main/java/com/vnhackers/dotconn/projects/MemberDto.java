// DTO pentru un membru al echipei (proprietar sau programator cu invitație acceptată).
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperRole;
import java.time.Instant;

public record MemberDto(
    Long userId,
    String displayName,
    DeveloperRole role,
    Availability availability,
    boolean owner,
    String assignedRole,
    Instant joinedAt) {}
