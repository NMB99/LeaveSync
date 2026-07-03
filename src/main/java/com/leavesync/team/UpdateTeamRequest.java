package com.leavesync.team;

import java.util.UUID;

public record UpdateTeamRequest(

        String name,
        UUID managerId

) {
}
