package com.leavesync.publicholiday;

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

@Tag(name = "Public Holidays", description = "Public holiday management - creation, retrieval, updates and deletion")
@RestController
@RequestMapping("/api/public-holidays")
@RequiredArgsConstructor
public class PublicHolidayController {

    private final PublicHolidayService publicHolidayService;

    @Operation(summary = "Create public holidays", description = "Creates a batch of public holidays for a specified region. If any holiday in the batch conflicts with an existing date and region, the entire batch is rejected and a 409 is returned with validation errors. Cache is cleared on success. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "201", description = "Public holiday created successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "409", description = "One or more holidays already exist for the same date and region - full batch rejected")
    @PostMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<CreatePublicHolidaysResponse> createPublicHolidays(
            @Valid @RequestBody CreatePublicHolidaysRequest request
    ) {

        CreatePublicHolidaysResponse response = publicHolidayService.createPublicHolidays(request);

        if (!response.errors().isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get public holidays", description = "Returns a list of public holidays. Optionally filter by region and/or year. All parameters are optional. Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Public holidays returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping
    public ResponseEntity<List<PublicHolidayResponse>> getPublicHolidays(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.getPublicHolidays(region, year));
    }

    @Operation(summary = "Get public holiday by ID", description = "Returns a single public holiday by ID. Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Public holiday returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "404", description = "Public holiday not found")
    @GetMapping("/{id}")
    public ResponseEntity<PublicHolidayResponse> getPublicHolidayById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.getPublicHolidayById(id));
    }

    @Operation(summary = "Update public holiday", description = "Updates a public holiday's date and name. Blocked if another holiday already exists for the same date and region. Cache is cleared on update. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Public holiday updated successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid request body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Public holiday not found")
    @ApiResponse(responseCode = "409", description = "A holiday already exists for the same date and region")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<PublicHolidayResponse> updatePublicHoliday(
            @PathVariable UUID id,
            @Valid @RequestBody PublicHolidayRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.updatePublicHoliday(id, request));
    }

    @Operation(summary = "Delete public holiday", description = "Deletes a public holiday by ID. Cache is cleared on deletion. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "204", description = "Public holiday deleted successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @ApiResponse(responseCode = "404", description = "Public holiday not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<Void> deletePublicHoliday(
            @PathVariable UUID id
    ) {
        publicHolidayService.deletePublicHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
