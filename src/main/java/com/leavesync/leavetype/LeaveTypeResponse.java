package com.leavesync.leavetype;

import com.leavesync.entity.LeaveType;

import java.util.UUID;

public record LeaveTypeResponse(

        UUID id,
        String name,
        boolean requiresBalanceTracking,
        boolean requiresHrApproval,
        boolean requiresReason

) {
    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getName(),
                leaveType.isRequiresBalanceTracking(),
                leaveType.isRequiresHrApproval(),
                leaveType.isRequiresReason()
        );
    }
}
