package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.enums.OfferStatus;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferResultResponse(
        UUID offerId,
        UUID bookingId,
        OfferStatus status,
        BigDecimal estimatedMin,
        BigDecimal estimatedMax,
        Integer estimatedArrivalMinutes,
        String note
) {
    public static OfferResultResponse from(BookingOffer o) {
        return new OfferResultResponse(
                o.getId(),
                o.getBooking().getId(),
                o.getStatus(),
                o.getEstimatedMin(),
                o.getEstimatedMax(),
                o.getEstimatedArrivalMinutes(),
                o.getNote()
        );
    }
}