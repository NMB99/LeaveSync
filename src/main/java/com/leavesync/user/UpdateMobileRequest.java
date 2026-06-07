package com.leavesync.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateMobileRequest(

        @NotBlank(message = "Mobile number is required")
        String mobileNumber

) {
}
