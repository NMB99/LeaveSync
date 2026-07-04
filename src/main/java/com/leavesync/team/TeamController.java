package com.leavesync.team;

import com.leavesync.common.TeamPageResponse;
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

@Tag(name = "Teams", description = "Team creation, retrieval, updates and deletion")
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Create team", description = "Creates a new team with a designated manager. Manager must be an active user with MANAGER role. Team name must be unique. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "201", description = "Team created successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Manager not found")
    @ApiResponse(responseCode = "409", description = "Team name already exists")
    @PostMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<TeamResponse> createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        TeamResponse response = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get all teams", description = "Returns a paginated list of teams. MANAGER sees only their own teams. HR and ADMIN see all teams. Accessible by: ADMIN, HR, MANAGER")
    @ApiResponse(responseCode = "200", description = "Teams returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN, HR or MANAGER role required")
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    public ResponseEntity<TeamPageResponse> getAllTeams(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK).body(teamService.getAllTeams(principal, pageable));
    }

    @Operation(summary = "Get team by ID", description = "Returns a single team by ID. MANAGER can only access teams they manage. ADMIN and HR can access any team.")
    @ApiResponse(responseCode = "200", description = "Team returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "MANAGER can only access teams they manage")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    public ResponseEntity<TeamResponse> getTeamById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        TeamResponse response = teamService.getTeamById(id, principal);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Update team", description = "Updates a team's name or manager. New manager must be an active user with MANAGER role. Team name must be unique. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Team updated successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Team or manager not found")
    @ApiResponse(responseCode = "409", description = "Team name already exists")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        TeamResponse response = teamService.updateTeam(id, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Delete team", description = "Deletes a team by ID. Blocked if the team has active members. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "204", description = "Team deleted successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @ApiResponse(responseCode = "409", description = "Cannot delete a team with active members")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable UUID id
    ) {
        teamService.deleteTeam(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
