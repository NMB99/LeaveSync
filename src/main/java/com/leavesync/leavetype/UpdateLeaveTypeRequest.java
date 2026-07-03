package com.leavesync.leavetype;

import jakarta.validation.constraints.NotBlank;

public record UpdateLeaveTypeRequest(

        @NotBlank(message = "Leave type name is required")
        String name,

        boolean requiresHrApproval,
        boolean requiresReason,
        boolean requiresBalanceTracking

) {
}
