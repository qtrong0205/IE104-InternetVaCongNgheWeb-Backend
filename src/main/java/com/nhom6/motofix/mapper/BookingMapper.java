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
    Booking toBooking(CreateBookingRequest request);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "providerId", source = "provider.id")
    @Mapping(target = "totalAmount", expression = "java(extractAcceptedTotalAmount(booking))")
    BookingResponse toBookingResponse(Booking booking);

    default BigDecimal extractAcceptedTotalAmount(Booking booking) {
        if (booking == null || booking.getQuotes() == null) {
            return null;
        }
        return booking.getQuotes().stream()
                .filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                .map(com.nhom6.motofix.entity.RepairQuote::getTotalAmount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    UploadMediaResponse toUploadMediaResponse(BookingMedia media);
}
