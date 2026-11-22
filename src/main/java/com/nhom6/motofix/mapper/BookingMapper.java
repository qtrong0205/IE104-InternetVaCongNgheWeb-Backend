package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.UploadMediaResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingMedia;
import com.nhom6.motofix.enums.QuoteStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "offers", ignore = true)
    @Mapping(target = "quotes", ignore = true)
    @Mapping(target = "media", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "searchRound", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "review", ignore = true)
    Booking toBooking(CreateBookingRequest request);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "providerId", source = "provider.id")
    @Mapping(target = "completedAt", source = "completedAt")
    @Mapping(target = "reviewDeadline", expression = "java(extractReviewDeadline(booking))")
    @Mapping(target = "isReviewed", expression = "java(booking.getReview() != null)")
    @Mapping(target = "totalAmount", expression = "java(extractAcceptedTotalAmount(booking))")
    BookingResponse toBookingResponse(Booking booking);

    default java.time.LocalDateTime extractReviewDeadline(Booking booking) {
        if (booking == null) {
            return null;
        }
        java.time.LocalDateTime completedAt = booking.getCompletedAt();
        if (completedAt == null && booking.getStatus() == com.nhom6.motofix.enums.BookingStatus.COMPLETED) {
            completedAt = booking.getUpdatedAt() != null ? booking.getUpdatedAt() : booking.getCreatedAt();
        }
        return completedAt != null ? completedAt.plusHours(168) : null;
    }

    default BigDecimal extractAcceptedTotalAmount(Booking booking) {
        if (booking == null || booking.getQuotes() == null) {
            return null;
        }
        return booking.getQuotes().stream()
                .filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                .sorted(java.util.Comparator.comparing(com.nhom6.motofix.entity.RepairQuote::getVersion, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(com.nhom6.motofix.entity.RepairQuote::getTotalAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    UploadMediaResponse toUploadMediaResponse(BookingMedia media);
}
