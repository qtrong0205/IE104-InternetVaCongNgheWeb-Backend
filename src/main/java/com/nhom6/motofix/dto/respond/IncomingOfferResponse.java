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

    public static IncomingOfferResponse from(com.nhom6.motofix.entity.BookingOffer o) {
        if (o == null) return null;
        com.nhom6.motofix.entity.Booking b = o.getBooking();
        List<UploadMediaResponse> mediaResponses = (b != null && b.getMedia() != null) ?
                b.getMedia().stream()
                        .map(m -> new UploadMediaResponse(m.getUrl(), m.getType()))
                        .toList() : null;

        return new IncomingOfferResponse(
                o.getId(),
                b != null ? b.getId() : null,
                o.getStatus(),
                b != null ? b.getProblemDescription() : null,
                b != null ? b.getAddress() : null,
                b != null ? b.getLatitude() : null,
                b != null ? b.getLongitude() : null,
                b != null ? b.getVehicleBrand() : null,
                b != null ? b.getVehicleModel() : null,
                b != null ? b.getVehicleLicensePlate() : null,
                mediaResponses,
                o.getDistanceM(),
                o.getCreatedAt(),
                o.getExpiresAt(),
                o.getEstimatedMin(),
                o.getEstimatedMax(),
                o.getEstimatedArrivalMinutes(),
                o.getNote()
        );
    }
}
