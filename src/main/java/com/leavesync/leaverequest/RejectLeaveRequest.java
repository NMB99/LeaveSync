package com.leavesync.leaverequest;

import jakarta.validation.constraints.NotBlank;

public record RejectLeaveRequest(

        @NotBlank(message = "Rejection reason is required")
        String reason
) {
}
