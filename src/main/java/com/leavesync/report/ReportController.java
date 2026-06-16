package com.leavesync.report;

import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/whos-off")
    public ResponseEntity<List<WhosOffResponse>> getWhosOff(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getWhosOff(principal, date));
    }

    @GetMapping("/balance-summary")
    public ResponseEntity<List<BalanceSummaryResponse>> getBalanceSummary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam int year
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getBalanceSummary(principal, year));
    }

    @GetMapping("/leave-history")
    public ResponseEntity<List<LeaveHistoryResponse>> getLeaveHistory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getLeaveHistory(principal, userId, startDate, endDate));
    }

    @GetMapping("/absence-patterns")
    public ResponseEntity<List<AbsencePatternResponse>> getAbsencePatterns(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(reportService.getAbsencePatterns(principal, startDate, endDate));
    }

    @GetMapping("/whos-off/export-csv")
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

    @GetMapping("/balance-summary/export-csv")
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

    @GetMapping("/leave-history/export-csv")
    public ResponseEntity<String> exportLeaveHistoryAsCsv(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        String csv = CsvExportUtil.exportToCsv(reportService.getLeaveHistory(principal, userId, startDate, endDate));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leave-history-report.csv\"")
                .body(csv);
    }

    @GetMapping("/absence-patterns/export-csv")
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
