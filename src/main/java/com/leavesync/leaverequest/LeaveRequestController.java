package com.leavesync.leaverequest;

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

@Tag(name = "Leave Requests", description = "Leave request submission, approval, rejection, cancellation and status tracking")
@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @Operation(summary = "Create leave request", description = "Submits a new leave request. Routes to the employee's manager for approval. If no manager found or inactive, automatically rerouted to HR. Leave types requiring HR approval always route directly to HR. SICK leave cannot be future-dated. Half-day requests must span a single day. Response includes warnings for insufficient balance, notice period violations, and overlapping requests. ADMIN cannot submit leave. Accessible by: EMPLOYEE, MANAGER, HR")
    @ApiResponse(responseCode = "201", description = "Leave request submitted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "ADMIN accounts cannot submit leave requests")
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR')")
    public ResponseEntity<LeaveRequestResponse> createLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SubmitLeaveRequest request
    ) {
        UUID userId = principal.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveRequestService.submit(userId, request));
    }

    @Operation(summary = "Get my leave requests", description = "Returns a paginated list of the currently authenticated user's own leave requests regardless of status. Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Leave requests returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping("/my")
    public ResponseEntity<PageResponse<LeaveRequestResponse>> getMyLeaveRequests(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.getMyLeaveRequests(principal.userId(), pageable));
    }

    @Operation(summary = "Get leave requests", description = "Returns leave requests scoped by role. EMPLOYEE sees their own. MANAGER sees pending requests for their team members. HR sees pending, escalated and rerouted requests assigned to HR. ADMIN sees pending, escalated and rerouted requests assigned to ADMIN.")
    @ApiResponse(responseCode = "200", description = "Leave requests returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public ResponseEntity<PageResponse<LeaveRequestResponse>> getLeaveRequests(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.getLeaveRequests(principal, pageable));
    }

    @Operation(summary = "Get leave request by ID", description = "Returns a single leave request by ID. EMPLOYEE can only view their own. MANAGER can view their own and their team members'. HR and ADMIN can view any request.")
    @ApiResponse(responseCode = "200", description = "Leave request returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Access outside allowed scope")
    @ApiResponse(responseCode = "404", description = "Leave request not found")
    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequestResponse> getLeaveRequestById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.getLeaveRequestById(principal, id));
    }

    @Operation(summary = "Cancel leave request", description = "Cancels a leave request. Users can only cancel their own requests. Only PENDING, ESCALATED or REROUTED_TO_HR requests can be cancelled. Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Leave request cancelled successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "You can only cancel your own leave request")
    @ApiResponse(responseCode = "404", description = "Leave request not found")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<LeaveRequestResponse> cancelLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.cancelLeaveRequest(principal, id));
    }

    @Operation(summary = "Approve leave request", description = "Approves a leave request. MANAGER can only approve their own team members' non-HR-approval requests. HR can approve any request. ADMIN can only approve HR users' leave as a fallback. Self-approval not permitted. Accessible by: MANAGER, HR, ADMIN")
    @ApiResponse(responseCode = "200", description = "Leave request approved successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Action not permitted — self-approval, wrong team scope, or invalid role for this leave type")
    @ApiResponse(responseCode = "404", description = "Leave request not found")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    public ResponseEntity<LeaveRequestResponse> approveLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.approveLeaveRequest(principal, id));
    }

    @Operation(summary = "Reject leave request", description = "Rejects a leave request with a mandatory reason. Same role restrictions as approve apply. Employee is notified via email with the rejection reason. Accessible by: MANAGER, HR, ADMIN")
    @ApiResponse(responseCode = "200", description = "Leave request rejected successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Action not permitted — self-approval, wrong team scope, or invalid role for this leave type")
    @ApiResponse(responseCode = "404", description = "Leave request not found")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    public ResponseEntity<LeaveRequestResponse> rejectLeaveRequest(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody RejectLeaveRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveRequestService.rejectLeaveRequest(principal, id, request));
    }

}
