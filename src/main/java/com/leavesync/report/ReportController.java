package com.leavesync.report;

import com.leavesync.common.PageResponse;
import com.leavesync.leavebalance.LeaveBalanceResponse;
import com.leavesync.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Reports", description = "On-demand leave reports with CSV export - who's off, balance summary, leave history and absence patterns")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Get who's off", description = "Returns a paginated list of employees on leave for a specified date. Includes PENDING and APPROVED requests. MANAGER sees their team only. HR and ADMIN see company-wide. Requires a date query parameter in ISO format (yyyy-MM-dd). Accessible by: ADMIN, HR, MANAGER")
    @ApiResponse(responseCode = "200", description = "Who's off returned successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid date parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN, HR or MANAGER role required")
    @GetMapping("/whos-off")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<PageResponse<WhosOffResponse>> getWhosOff(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getWhosOff(principal, date, pageable));
    }

    @Operation(summary = "Get balance summary", description = "Returns a paginated company-wide leave balance summary for the specified year. Requires a year query parameter. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Balance summary returned successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid year parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/balance-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<PageResponse<LeaveBalanceResponse>> getBalanceSummary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getBalanceSummary(principal, year, pageable));
    }

    @Operation(summary = "Get leave history", description = "Returns a paginated full audit trail of leave requests including status changes, actioned by, and timestamps. Optionally filter by userId, startDate and endDate. All parameters are optional. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Leave history returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/leave-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<PageResponse<LeaveHistoryResponse>> getLeaveHistory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getLeaveHistory(principal, userId, startDate, endDate, pageable));
    }

    @Operation(summary = "Get absence patterns", description = "Returns a paginated sick leave absence pattern report - frequency of sick leave instances per employee. Counts APPROVED and REJECTED sick leave only. Optionally filter by date range. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Absence patterns returned successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/absence-patterns")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<PageResponse<AbsencePatternResponse>> getAbsencePatterns(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getAbsencePatterns(principal, startDate, endDate, pageable));
    }

    @Operation(summary = "Export who's off", description = "Exports who's off report as a CSV file. Same filters and access rules as the JSON endpoint. Returns file attachment with Content-Disposition header. Accessible by: ADMIN, HR, MANAGER")
    @ApiResponse(responseCode = "200", description = "Who's off CSV report exported successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid date parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN, HR or MANAGER role required")
    @GetMapping("/whos-off/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<String> exportWhosOffAsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String csv = CsvExportUtil.exportToCsv(reportService.getWhosOff(principal, date));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"whos-off-report.csv\"")
                .body(csv);
    }

    @Operation(summary = "Export balance summary", description = "Exports balance summary report as a CSV file. Same filters and access rules as the JSON endpoint. Returns file attachment with Content-Disposition header. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Balance summary CSV report exported successfully")
    @ApiResponse(responseCode = "400", description = "Missing or invalid year parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/balance-summary/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<String> exportBalanceSummaryAsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year
    ) {
        String csv = CsvExportUtil.exportToCsv(reportService.getBalanceSummary(principal, year));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"balance-summary-report.csv\"")
                .body(csv);
    }

    @Operation(summary = "Export leave history", description = "Exports leave history report as a CSV file. Same filters and access rules as the JSON endpoint. Returns file attachment with Content-Disposition header. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Leave history CSV report exported successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/leave-history/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<String> exportLeaveHistoryAsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        String csv = CsvExportUtil.exportLeaveHistoryToCsv(reportService.getLeaveHistory(principal, userId, startDate, endDate));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leave-history-report.csv\"")
                .body(csv);
    }

    @Operation(summary = "Export absence pattern", description = "Exports absence pattern report as a CSV file. Same filters and access rules as the JSON endpoint. Returns file attachment with Content-Disposition header. Accessible by: ADMIN, HR")
    @ApiResponse(responseCode = "200", description = "Absence pattern CSV report exported successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN or HR role required")
    @GetMapping("/absence-patterns/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<String> exportAbsencePatternsAsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        String csv = CsvExportUtil.exportAbsencePatternsToCsv(reportService.getAbsencePatterns(principal, startDate, endDate));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"absence-patterns-report.csv\"")
                .body(csv);
    }
}
