package com.leavesync.user;

import com.leavesync.common.PageResponse;
import com.leavesync.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Users", description = "User creation, invite flow, password management, profile updates and deactivation")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create user", description = "Creates a new user and sends an invite email. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "201", description = "User created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Unauthorised - ADMIN or HR required")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Accept invite", description = "Allows a newly invited user to set their password using the token sent via email. Publicly accessible.")
    @ApiResponse(responseCode = "204", description = "Password set successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(
            @Valid @RequestBody AcceptInviteRequest request
    ) {
        userService.acceptInvite(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forgot password", description = "Sends a reset token via email. Publicly accessible.")
    @ApiResponse(responseCode = "204", description = "Forgot password request sent successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        userService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reset password", description = "Resets the user's password using a valid reset token received via email. Publicly accessible.")
    @ApiResponse(responseCode = "204", description = "Password reset successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get all users", description = "Returns a paginated list of users scoped by role. ADMIN and HR see all users, MANAGER sees their team members only. EMPLOYEE access is forbidden.")
    @ApiResponse(responseCode = "200", description = "Users returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions — EMPLOYEE access is forbidden")
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getAllUsers(principal, pageable));
    }

    @Operation(summary = "Get a user by id", description = "Returns a single user by their ID. ADMIN and HR can access any user. MANAGER can only access users within their team. EMPLOYEE can only access their own profile.")
    @ApiResponse(responseCode = "200", description = "User returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions — access outside allowed scope")
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(userService.getUserById(id, principal));
    }

    @Operation(summary = "Update user", description = "Updates a user's details by ID. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Unauthorised - ADMIN or HR required")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "Update mobile number", description = "Updates the mobile number for the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Mobile number updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @PatchMapping("/me/mobile")
    public ResponseEntity<UserResponse> updateMobile(
            @Valid @RequestBody UpdateMobileRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(userService.updateMobile(request, principal));
    }

    @Operation(summary = "Deactivate user", description = "Deactivates a user account by ID. Blocked if: the user currently manages a team, the user is the last active ADMIN, or the user is already inactive. Accessible by: ADMIN only")
    @ApiResponse(responseCode = "200", description = "User deactivated successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Unauthorised - ADMIN required only")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.deactivateUser(id));
    }

}
