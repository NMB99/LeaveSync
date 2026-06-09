package com.leavesync.leaverequest;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record SubmitLeaveRequest(

        @NotNull(message = "Leave type is required")
        UUID leaveTypeId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        boolean isHalfDay,

        String reason

) {
}
