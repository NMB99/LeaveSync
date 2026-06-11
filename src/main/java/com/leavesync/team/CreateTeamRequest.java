package com.leavesync.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTeamRequest(

        @NotBlank(message = "Team name is required")
        String name,

        @NotNull(message = "Manager Id is required")
        UUID managerId
        
) {
}
