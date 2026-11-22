package com.nhom6.motofix.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateBookingRequest(
        @NotBlank
        @Size(max = 2000)
        String problemDescription,

        @Size(max = 10)
        List<@NotNull @Valid MediaRequest> media,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        @NotBlank
        String address,

        @NotBlank
        @Size(max = 100)
        String vehicleBrand,

        @NotBlank
        @Size(max = 100)
        String vehicleModel,

        @Size(max = 30)
        String vehicleLicensePlate
) {
    public CreateBookingRequest(
            String problemDescription,
            List<MediaRequest> media,
            Double latitude,
            Double longitude
    ) {
        this(problemDescription, media, latitude, longitude, null, null, null, null);
    }
}

