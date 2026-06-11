package com.leavesync.team;

import com.leavesync.entity.Team;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamResponse(

        UUID id,
        String name,
        UUID managerId,
        LocalDateTime createdAt

) {
    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getManagerId(),
                team.getCreatedAt()
        );
    }
}
