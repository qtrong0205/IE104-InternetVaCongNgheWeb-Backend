package com.nhom6.motofix.dto.respond;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID bookingId,
        UUID senderId,
        String senderName,
        String senderRole,
        String message,
        LocalDateTime createdAt
) {}
