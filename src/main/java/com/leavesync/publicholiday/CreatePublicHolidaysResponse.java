package com.leavesync.publicholiday;

import java.util.List;

public record CreatePublicHolidaysResponse(

        List<PublicHolidayResponse> saved,
        List<PublicHolidayValidationError> errors

) {
}
