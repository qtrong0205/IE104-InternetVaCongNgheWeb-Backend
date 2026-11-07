package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingOfferSummaryResponse(
        UUID id,
        UUID offerId,
        UUID bookingId,
        OfferStatus status,
        BigDecimal estimatedMin,
        BigDecimal estimatedMax,
        Integer estimatedArrivalMinutes,
        String note,
        Double distanceM,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        UUID providerId,
        String businessName,
        String fullName,
        String phone,
        String avatarUrl,
        Float ratingAvg,
        int ratingCount,
        Float baseLat,
        Float baseLng
) {
}
