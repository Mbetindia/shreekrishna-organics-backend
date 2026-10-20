
package com.shreekrishna.organics.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(

        @NotBlank(message = "MSG91 access token is required")
        String accessToken

) {
}
