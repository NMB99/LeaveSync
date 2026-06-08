package com.leavesync.workingday;

import java.time.LocalDate;

public record WorkingDayResponse(

        LocalDate from,
        LocalDate to,
        int workingDays

) {
}
