package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.UploadMediaResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "offers", ignore = true)
    @Mapping(target = "quotes", ignore = true)
    @Mapping(target = "media", ignore = true)
    Booking toBooking(CreateBookingRequest request);

    @Mapping(target = "customerId", source = "customer.id")
    BookingResponse toBookingResponse(Booking booking);

    UploadMediaResponse toUploadMediaResponse(BookingMedia media);
}