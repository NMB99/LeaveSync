package com.leavesync.leavetype;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Leave Types", description = "Leave type retrieval and configurations")
@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    @Operation(summary = "Get leave types", description = "Returns a list of all configured leave types including HR approval requirements, reason requirements and balance tracking settings. Results are cached. Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Leave types returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public ResponseEntity<List<LeaveTypeResponse>> getLeaveTypes() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(leaveTypeService.getLeaveTypes());
    }

    @Operation(summary = "Update leave type", description = "Updates a leave type's name, HR approval requirement, reason requirement and balance tracking settings. Leave type name must be unique. Cache is cleared on update. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Leave type updated successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Leave type not found")
    @ApiResponse(responseCode = "409", description = "Leave type name already exists")
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
