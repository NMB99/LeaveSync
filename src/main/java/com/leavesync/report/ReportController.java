package com.leavesync.report;

import com.leavesync.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
}
