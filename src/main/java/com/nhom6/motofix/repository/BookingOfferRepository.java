package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingOfferRepository extends JpaRepository<BookingOffer, UUID> {
    List<BookingOffer> findByProvider_IdAndStatus(UUID providerId, OfferStatus status);
}
