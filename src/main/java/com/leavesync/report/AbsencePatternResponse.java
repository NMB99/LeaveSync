package com.leavesync.report;

import com.leavesync.enums.LeaveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AbsencePatternResponse(

        UUID userId,
        String employeeName,
        int instanceCount,
        List<SickLeaveInstance> instances

) {
    public record SickLeaveInstance(

            UUID leaveRequestId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalWorkingDays,
            LeaveStatus status

    ) {
    }
}
