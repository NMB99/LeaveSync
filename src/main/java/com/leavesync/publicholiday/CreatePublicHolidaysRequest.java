package com.leavesync.publicholiday;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreatePublicHolidaysRequest(

        @NotBlank(message = "Region is required")
        String region,

        @NotEmpty(message = "At least one holiday is required")
        @Valid
        List<PublicHolidayRequest> holidays

) {
}
