package com.leavesync.leaverequest;

import com.leavesync.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
