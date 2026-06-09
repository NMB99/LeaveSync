package com.leavesync.leaverequest;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.enums.LeaveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(

        UUID id,
        UUID userId,
        UUID leaveTypeId,
        LocalDate startDate,
        LocalDate endDate,
        boolean isHalfDay,
        String reason,
        BigDecimal totalWorkingDays,
        LeaveStatus status,
        boolean balanceWarning,
        boolean noticePeriodWarning,
        boolean overlapWarning

) {
    public static LeaveRequestResponse from(LeaveRequest request) {
        return new LeaveRequestResponse(
                request.getId(),
                request.getUserId(),
                request.getLeaveTypeId(),
                request.getStartDate(),
                request.getEndDate(),
                request.isHalfDay(),
                request.getReason(),
                request.getTotalWorkingDays(),
                request.getStatus(),
                false,
                request.isNoticePeriodWarning(),
                request.isOverlapWarning()
        );
    }

    public static LeaveRequestResponse from(LeaveRequest request, boolean balanceWarning) {
        return new LeaveRequestResponse(
                request.getId(),
                request.getUserId(),
                request.getLeaveTypeId(),
                request.getStartDate(),
                request.getEndDate(),
                request.isHalfDay(),
                request.getReason(),
                request.getTotalWorkingDays(),
                request.getStatus(),
                balanceWarning,
                request.isNoticePeriodWarning(),
                request.isOverlapWarning()
        );
    }
}
