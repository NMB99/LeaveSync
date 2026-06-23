package com.leavesync.report;

import com.leavesync.entity.AuditLog;
import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.LeaveType;
import com.leavesync.entity.User;
import com.leavesync.enums.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LeaveHistoryResponse(

        UUID leaveRequestId,
        String employeeName,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        List<StatusChangeHistory> statusHistory

) {
    public record StatusChangeHistory(

            UUID auditLogId,
            LeaveStatus previousStatus,
            LeaveStatus newStatus,
            String actionedBy,
            LocalDateTime changedAt,
            String notes

    ) {
    }

    public static LeaveHistoryResponse from(
            LeaveRequest request,
            User employee,
            LeaveType leaveType,
            List<AuditLog> auditLog,
            Map<UUID, User> actionedBy
    ) {
        List<StatusChangeHistory> history = auditLog.stream()
                .map(log -> new StatusChangeHistory(
                        log.getId(),
                        log.getPreviousStatus(),
                        log.getNewStatus(),
                        log.getActionedBy() == null
                                ? "System"
                                : actionedBy.getOrDefault(log.getActionedBy(), null) == null
                                  ? "System"
                                  : actionedBy.get(log.getActionedBy()).getFirstName() + " " + actionedBy.get(log.getActionedBy()).getLastName(),
                        log.getChangedAt(),
                        log.getNotes()
                ))
                .toList();

        return new LeaveHistoryResponse(
                request.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                leaveType.getName(),
                request.getStartDate(),
                request.getEndDate(),
                history
        );
    }
}
