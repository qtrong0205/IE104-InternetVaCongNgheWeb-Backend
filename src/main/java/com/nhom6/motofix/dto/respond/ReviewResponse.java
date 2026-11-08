package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.entity.Review;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID providerId,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getProvider().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}

