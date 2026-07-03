package com.leavesync.workingday;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkingDayResponse(

        LocalDate from,
        LocalDate to,
        BigDecimal workingDays

) {
}
