package com.nhom6.motofix.mapper;

import com.nhom6.motofix.dto.respond.OfferResultResponse;
import com.nhom6.motofix.entity.BookingOffer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OfferMapper {
    @Mapping(target = "offerId", source = "id")
    @Mapping(target = "bookingId", source = "booking.id")
    OfferResultResponse toOfferResultResponse(BookingOffer offer);
}