package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        UUID id,
        BookingStatus status,
        String problemDescription,
        String address,
        Double latitude,
        Double longitude,
        String vehicleBrand,
        String vehicleModel,
        String vehicleLicensePlate,
        List<UploadMediaResponse> media,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID customerId,
        UUID providerId,
        PaymentMethod paymentMethod,
        BigDecimal totalAmount,
        Integer searchRound
) {
    public BookingResponse(
            UUID id,
            BookingStatus status,
            String problemDescription,
            List<UploadMediaResponse> media,
            Double latitude,
            Double longitude,
            LocalDateTime createdAt,
            UUID customerId,
            UUID providerId,
            PaymentMethod paymentMethod,
            BigDecimal totalAmount
    ) {
        this(id, status, problemDescription, null, latitude, longitude, null, null, null, media, createdAt, null, customerId, providerId, paymentMethod, totalAmount, 1);
    }

    public BookingResponse(
            UUID id,
            BookingStatus status,
            String problemDescription,
            String address,
            Double latitude,
            Double longitude,
            String vehicleBrand,
            String vehicleModel,
            String vehicleLicensePlate,
            List<UploadMediaResponse> media,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            UUID customerId,
            UUID providerId,
            PaymentMethod paymentMethod,
            BigDecimal totalAmount
    ) {
        this(id, status, problemDescription, address, latitude, longitude, vehicleBrand, vehicleModel, vehicleLicensePlate, media, createdAt, updatedAt, customerId, providerId, paymentMethod, totalAmount, 1);
    }
}

