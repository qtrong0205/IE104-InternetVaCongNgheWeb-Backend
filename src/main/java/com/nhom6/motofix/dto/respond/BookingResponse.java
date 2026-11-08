package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.PaymentMethod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        String problemDescription,
        List<UploadMediaResponse> media,
        Double latitude,
        Double longitude,
        LocalDateTime createdAt,
        UUID customerId,
        UUID providerId,
        PaymentMethod paymentMethod
) {
}
