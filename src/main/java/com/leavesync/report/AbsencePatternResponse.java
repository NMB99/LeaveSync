package com.leavesync.report;

import com.leavesync.entity.LeaveRequest;
import com.leavesync.entity.User;
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

    public static AbsencePatternResponse from(
            User employee,
            List<LeaveRequest> requests
    ) {
        List<SickLeaveInstance> instances = requests.stream()
                .map(request -> new SickLeaveInstance(
                        request.getId(),
                        request.getStartDate(),
                        request.getEndDate(),
                        request.getTotalWorkingDays(),
                        request.getStatus()
                ))
                .toList();

        return new AbsencePatternResponse(
                employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                instances.size(),
                instances
        );
    }
}
