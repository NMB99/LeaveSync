package com.leavesync.publicholiday;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public-holidays")
@RequiredArgsConstructor
public class PublicHolidayController {

    private final PublicHolidayService publicHolidayService;

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

    @GetMapping
    public ResponseEntity<List<PublicHolidayResponse>> getPublicHolidays(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.getPublicHolidays(region, year));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicHolidayResponse> getPublicHolidayById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.getPublicHolidayById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<PublicHolidayResponse> updatePublicHoliday(
            @PathVariable UUID id,
            @Valid @RequestBody PublicHolidayRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(publicHolidayService.updatePublicHoliday(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<Void> deletePublicHoliday(
            @PathVariable UUID id
    ) {
        publicHolidayService.deletePublicHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
