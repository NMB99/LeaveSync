package com.leavesync.report;

import com.leavesync.entity.AuditLog;
import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.LeaveType;
import com.leavesync.entity.User;
import com.leavesync.enums.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeaveHistoryResponse(

        UUID auditLogId,
        UUID leaveRequestId,
        String employeeName,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        LeaveStatus previousStatus,
        LeaveStatus newStatus,
        String actionedBy,
        LocalDateTime changedAt,
        String notes

) {
    public static LeaveHistoryResponse from(AuditLog auditLog, LeaveRequest request,
                                            User employee, LeaveType leaveType,
                                            User actionedBy) {
        return new LeaveHistoryResponse(
                auditLog.getId(),
                request.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                leaveType.getName(),
                request.getStartDate(),
                request.getEndDate(),
                auditLog.getPreviousStatus(),
                auditLog.getNewStatus(),
                actionedBy == null
                        ? "System"
                        : actionedBy.getFirstName() + " " + actionedBy.getLastName(),
                auditLog.getChangedAt(),
                auditLog.getNotes()
        );
    }
}
