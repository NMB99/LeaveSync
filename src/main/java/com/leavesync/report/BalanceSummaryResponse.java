package com.leavesync.report;

import com.leavesync.entity.LeaveBalance;
import com.leavesync.entity.User;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceSummaryResponse(

        UUID userId,
        String employeeName,
        int year,
        BigDecimal totalEntitlement,
        BigDecimal carriedOver,
        BigDecimal leaveUsed,
        BigDecimal pendingDays,
        BigDecimal remainingBalance

) {
    public static BalanceSummaryResponse from(LeaveBalance balance, User user) {
        return new BalanceSummaryResponse(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                balance.getYear(),
                balance.getTotalEntitlement(),
                balance.getCarriedOver(),
                balance.getLeaveUsed(),
                balance.getPendingDays(),
                balance.getTotalEntitlement()
                        .add(balance.getCarriedOver())
                        .subtract(balance.getLeaveUsed())
                        .subtract(balance.getPendingDays())
        );
    }
}
