package com.leavesync.auth;

import com.leavesync.enums.Role;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        UUID userId,
        String email,
        Role role
) {
}
