package com.leavesync.publicholiday;

import java.time.LocalDate;

public record PublicHolidayValidationError(

        int index,
        LocalDate date,
        String name,
        String reason

) {
}
