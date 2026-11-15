package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.entity.CustomerVehicle;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String brand,
        String model,
        String licensePlate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleResponse from(CustomerVehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}

