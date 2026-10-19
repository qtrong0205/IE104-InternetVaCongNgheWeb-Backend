package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.enums.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        String problemDescription,
        List<String> mediaUrls,
        Double latitude,
        Double longitude,
        LocalDateTime createdAt,
        UUID providerId
) {
}
