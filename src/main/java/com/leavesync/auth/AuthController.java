package com.leavesync.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Login and logout endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Login", description = "Authenticate with email and password. Returns a JWT token.")
    @ApiResponse(responseCode = "200", description = "Login successful - JWT token returned")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Logout", description = "Invalidates the current JWT token. Token cannot be reused after logout.")
    @ApiResponse(responseCode = "204", description = "Logout successful")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

}
