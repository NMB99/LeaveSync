package com.leavesync.leaverequest;

import com.leavesync.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> createLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SubmitLeaveRequest request
    ) {
        UUID userId = principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveRequestService.submit(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<LeaveRequestResponse>> getLeaveRequests(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.getLeaveRequests(principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequestResponse> getLeaveRequestById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.getLeaveRequestById(principal, id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<LeaveRequestResponse> cancelLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.cancelLeaveRequest(principal, id));
    }
}
