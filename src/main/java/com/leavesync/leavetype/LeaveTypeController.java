package com.leavesync.leavetype;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    @GetMapping
    public ResponseEntity<List<LeaveTypeResponse>> getLeaveTypes() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveTypeService.getLeaveTypes());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<LeaveTypeResponse> updateLeaveType(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveTypeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveTypeService.updateLeaveType(id, request));
    }
}
