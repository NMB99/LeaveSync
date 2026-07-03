package com.leavesync.user;

import com.leavesync.entity.User;
import com.leavesync.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(

        UUID id,
        String firstName,
        String lastName,
        String email,
        String mobileNumber,
        Role role,
        UUID teamId,
        boolean isActive,
        LocalDateTime createdAt

) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getRole(),
                user.getTeamId(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
