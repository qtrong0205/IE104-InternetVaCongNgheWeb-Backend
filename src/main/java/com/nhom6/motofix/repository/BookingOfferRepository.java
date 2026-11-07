package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingOfferRepository extends JpaRepository<BookingOffer, UUID> {
    List<BookingOffer> findByProvider_IdAndStatus(UUID providerId, OfferStatus status);

    Optional<BookingOffer> findByIdAndProvider_Id(UUID id, UUID providerId);

    @Query("""
        SELECT o FROM BookingOffer o
        JOIN FETCH o.provider p
        JOIN FETCH p.user u
        WHERE o.booking.id = :bookingId AND o.status = :status
        ORDER BY o.createdAt DESC
        """)
    List<BookingOffer> findByBookingIdAndStatusWithProvider(
            @Param("bookingId") UUID bookingId,
            @Param("status") OfferStatus status);

    @Modifying
    @Query("""
        UPDATE BookingOffer o SET o.status = :expiredStatus
        WHERE o.booking.id = :bookingId
          AND o.id <> :selectedOfferId
          AND o.status IN (:activeStatuses)
        """)
    int expireRemainingOffers(
            @Param("bookingId") UUID bookingId,
            @Param("selectedOfferId") UUID selectedOfferId,
            @Param("expiredStatus") OfferStatus expiredStatus,
            @Param("activeStatuses") List<OfferStatus> activeStatuses);

    // Set status cho các offers hết hạn
    @Modifying
    @Query("""
        UPDATE BookingOffer o SET o.status = :to
        WHERE o.status = :from AND o.expiresAt < :now
        """)
    int expireOffers(@Param("from") OfferStatus from,
                     @Param("to") OfferStatus to,
                     @Param("now") LocalDateTime now);
}
