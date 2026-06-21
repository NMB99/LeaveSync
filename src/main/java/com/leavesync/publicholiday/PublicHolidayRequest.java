package com.leavesync.publicholiday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PublicHolidayRequest(

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Name is required")
        String name

) {
}
