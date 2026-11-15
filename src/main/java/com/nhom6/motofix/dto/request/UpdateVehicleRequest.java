package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
        @Size(max = 100, message = "Hãng xe không được vượt quá 100 ký tự")
        String brand,

        @Size(max = 100, message = "Dòng xe không được vượt quá 100 ký tự")
        String model,

        @JsonAlias({"license_plate", "licensePlate"})
        @Size(max = 30, message = "Biển số xe không được vượt quá 30 ký tự")
        String licensePlate
) {
}

