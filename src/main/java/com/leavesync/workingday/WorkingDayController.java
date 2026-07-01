package com.leavesync.workingday;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Working Days", description = "Utility endpoint for calculating working days between two dates, excluding weekends and public holidays")
@RestController
@RequestMapping("/api/working-days")
@RequiredArgsConstructor
public class WorkingDayController {

    private final WorkingDayService workingDayService;

    @Operation(summary = "Count working days", description = "Counts the number of working days between two dates (inclusive). Excludes weekends and public holidays for the configured default region. Both from and to parameters are required in ISO date format (yyyy-MM-dd). Accessible by: ALL authenticated users")
    @ApiResponse(responseCode = "200", description = "Working days counted successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid date parameters")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @GetMapping("/count")
    public ResponseEntity<WorkingDayResponse> countWorkingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(workingDayService.totalWorkingDaysResponse(from, to));
    }

}
