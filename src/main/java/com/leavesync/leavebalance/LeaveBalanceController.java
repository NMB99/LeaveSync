package com.leavesync.leavebalance;

import com.leavesync.common.LeaveBalancePageResponse;
import com.leavesync.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Leave Balances", description = "Leave balance enquiry for individuals and teams")
@RestController
@RequestMapping("/api/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    @Operation(summary = "Get my leave balance", description = "Returns the authenticated user's leave balance for the specified year. Requires a year query parameter. ADMIN accounts do not have a leave balance. Accessible by: EMPLOYEE, MANAGER, HR")
    @ApiResponse(responseCode = "200", description = "Leave balance returned successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid year parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "No leave balance found for the specified year")
    @GetMapping("/me")
    @PreAuthorize( "hasAnyRole('EMPLOYEE', 'MANAGER', 'HR') ")
    public ResponseEntity<LeaveBalanceResponse> getMyLeaveBalance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveBalanceService.getMyLeaveBalance(principal, year));
    }

    @Operation(summary = "Get leave balance by ID", description = "Returns the leave balance for a specific user by ID for the specified year. MANAGER can only access balances for users within their team. HR and ADMIN can access any user's balance. Accessible by: MANAGER, HR, ADMIN")
    @ApiResponse(responseCode = "200", description = "Leave balance returned successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid year parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Access outside allowed scope — MANAGER can only view their team members")
    @ApiResponse(responseCode = "404", description = "User not found or no leave balance exists for the specified year")
    @GetMapping("/{userId}")
    @PreAuthorize( "hasAnyRole('MANAGER', 'HR', 'ADMIN') ")
    public ResponseEntity<LeaveBalanceResponse> getLeaveBalanceById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId,
            @RequestParam int year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveBalanceService.getLeaveBalanceById(principal, userId, year));
    }

    @Operation(summary = "Get teams leave balance", description = "Returns a paginated list of leave balances for all members across the manager's teams for the specified year. Accessible by: MANAGER only")
    @ApiResponse(responseCode = "200", description = "Leave balances returned successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid year parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Unauthorised — MANAGER role required")
    @GetMapping
    @PreAuthorize( "hasRole('MANAGER') ")
    public ResponseEntity<LeaveBalancePageResponse> getTeamsLeaveBalance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveBalanceService.getTeamsLeaveBalance(principal, year, pageable));
    }
}
