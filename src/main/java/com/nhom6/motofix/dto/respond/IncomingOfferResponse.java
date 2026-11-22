package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhom6.motofix.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncomingOfferResponse(
        UUID offerId,
        UUID bookingId,
        OfferStatus status,
        String problemDescription,
        String address,
        Double latitude,
        Double longitude,
        String vehicleBrand,
        String vehicleModel,
        String vehicleLicensePlate,
        List<UploadMediaResponse> media,
        Double distanceM,
        LocalDateTime sentAt,
        LocalDateTime expiresAt,
        BigDecimal estimatedMin,
        BigDecimal estimatedMax,
        Integer estimatedArrivalMinutes,
        String note
) {
    public IncomingOfferResponse(
            UUID offerId,
            UUID bookingId,
            String problemDescription,
            Double latitude,
            Double longitude,
            Double distanceM,
            LocalDateTime sentAt,
            LocalDateTime expiresAt
    ) {
        this(offerId, bookingId, OfferStatus.PENDING, problemDescription, null, latitude, longitude, null, null, null, null, distanceM, sentAt, expiresAt, null, null, null, null);
    }
}
