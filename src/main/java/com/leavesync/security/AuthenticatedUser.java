package com.leavesync.security;

import com.leavesync.enums.Role;

import java.util.UUID;

public record AuthenticatedUser(

        UUID userId,
        String email,
        Role role

) {
}
