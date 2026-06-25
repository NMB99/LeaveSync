package com.leavesync.leavebalance;

import com.leavesync.common.PageResponse;
import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    @GetMapping("/me")
    @PreAuthorize( "hasAnyRole('EMPLOYEE', 'MANAGER', 'HR') ")
    public ResponseEntity<LeaveBalanceResponse> getMyLeaveBalance(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveBalanceService.getMyLeaveBalance(principal, year));
    }

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

    @GetMapping
    @PreAuthorize( "hasRole('MANAGER') ")
    public ResponseEntity<PageResponse<LeaveBalanceResponse>> getTeamsLeaveBalance(
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
