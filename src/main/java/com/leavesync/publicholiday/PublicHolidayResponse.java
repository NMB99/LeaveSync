package com.leavesync.publicholiday;

import java.time.LocalDate;
import java.util.UUID;

public record PublicHolidayResponse(

        UUID id,
        LocalDate date,
        String name,
        String region

) {
}
