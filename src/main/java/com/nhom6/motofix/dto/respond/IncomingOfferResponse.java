package com.nhom6.motofix.dto.respond;

import java.time.LocalDateTime;
import java.util.UUID;

public record IncomingOfferResponse(
        UUID offerId,
        UUID bookingId,
        String problemDescription,
        Double latitude,
        Double longitude,
        Double distanceM,
        LocalDateTime sentAt,
        LocalDateTime expiresAt
) {
}
