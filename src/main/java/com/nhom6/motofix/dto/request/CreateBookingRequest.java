package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

public record CreateBookingRequest(
        @NotBlank
        @Size(max = 2000)
        String problemDescription,

        @Size(max = 10)
        List<@NotBlank String> mediaUrls,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude
) {
}
