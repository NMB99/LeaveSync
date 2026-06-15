package com.leavesync.report;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.LeaveType;
import com.leavesync.entity.User;
import com.leavesync.enums.LeaveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WhosOffResponse(

        UUID leaveRequestId,
        UUID userId,
        String employeeName,
        String leaveType,
        LeaveStatus status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalWorkingDays

) {
    public static WhosOffResponse from(LeaveRequest request, User user, LeaveType leaveType) {
        return new WhosOffResponse(
                request.getId(),
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                leaveType.getName(),
                request.getStatus(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTotalWorkingDays()
        );
    }
}
