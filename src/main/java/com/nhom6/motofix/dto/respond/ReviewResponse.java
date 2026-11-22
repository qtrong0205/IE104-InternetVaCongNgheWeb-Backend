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
        String customerName,
        LocalDateTime createdAt
) {
    public ReviewResponse(UUID id, UUID bookingId, UUID providerId, Integer rating, String comment, LocalDateTime createdAt) {
        this(id, bookingId, providerId, rating, comment, null, createdAt);
    }

    public static ReviewResponse from(Review review) {
        String custName = null;
        if (review.getBooking() != null && review.getBooking().getCustomer() != null) {
            custName = review.getBooking().getCustomer().getFullName();
        }
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getProvider().getId(),
                review.getRating(),
                review.getComment(),
                custName,
                review.getCreatedAt()
        );
    }
}

