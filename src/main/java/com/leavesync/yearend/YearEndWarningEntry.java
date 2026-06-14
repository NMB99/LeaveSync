package com.leavesync.yearend;

import java.math.BigDecimal;

public record YearEndWarningEntry(

        String employeeFullName,
        BigDecimal remainingBalance,
        BigDecimal daysAtRisk

) {
}
