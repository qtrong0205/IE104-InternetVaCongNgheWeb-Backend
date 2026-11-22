package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingOfferRepository extends JpaRepository<BookingOffer, UUID> {
    List<BookingOffer> findByProvider_IdAndStatus(UUID providerId, OfferStatus status);

    Optional<BookingOffer> findByIdAndProvider_Id(UUID id, UUID providerId);

    List<BookingOffer> findByBookingId(UUID bookingId);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE BookingOffer o SET o.status = :expiredStatus
        WHERE o.booking.id = :bookingId
          AND o.status IN (:activeStatuses)
        """)
    int expireAllOffersForBooking(
            @Param("bookingId") UUID bookingId,
            @Param("expiredStatus") OfferStatus expiredStatus,
            @Param("activeStatuses") List<OfferStatus> activeStatuses);

    // Set status cho các offers hết hạn
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE BookingOffer o SET o.status = :to
        WHERE o.status = :from AND o.expiresAt < :now
        """)
    int expireOffers(@Param("from") OfferStatus from,
                     @Param("to") OfferStatus to,
                     @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE BookingOffer o SET o.status = :expiredStatus
        WHERE o.provider.id = :providerId
          AND o.status IN (:activeOfferStatuses)
          AND o.booking.id IN (SELECT b.id FROM Booking b WHERE b.status = :searchingStatus)
        """)
    int expireOffersForProvider(
            @Param("providerId") UUID providerId,
            @Param("expiredStatus") OfferStatus expiredStatus,
            @Param("activeOfferStatuses") Collection<OfferStatus> activeOfferStatuses,
            @Param("searchingStatus") BookingStatus searchingStatus);

    @Query("""
        SELECT o FROM BookingOffer o
        JOIN FETCH o.booking b
        JOIN FETCH o.provider p
        WHERE o.provider.id = :providerId
          AND o.status IN (:activeOfferStatuses)
          AND b.status = :searchingStatus
        """)
    List<BookingOffer> findWaitingOffersForProvider(
            @Param("providerId") UUID providerId,
            @Param("activeOfferStatuses") Collection<OfferStatus> activeOfferStatuses,
            @Param("searchingStatus") BookingStatus searchingStatus);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN TRUE ELSE FALSE END FROM BookingOffer o WHERE o.booking.id = :bookingId AND o.status = 'PENDING' AND o.expiresAt > :now")
    boolean hasActivePendingOffers(@Param("bookingId") UUID bookingId, @Param("now") LocalDateTime now);

    @Query("""
        SELECT o FROM BookingOffer o
        JOIN FETCH o.provider p
        WHERE o.booking.id = :bookingId
          AND o.status = 'ACCEPTED'
        """)
    List<BookingOffer> findAcceptedOffersWithProvider(@Param("bookingId") UUID bookingId);

    @Query("""
        SELECT o FROM BookingOffer o
        JOIN FETCH o.booking b
        LEFT JOIN FETCH b.media
        WHERE o.provider.id = :providerId
          AND b.status = :searchingStatus
          AND (
            (o.status = 'PENDING' AND o.expiresAt > :now)
            OR o.status = 'ACCEPTED'
          )
        """)
    List<BookingOffer> findIncomingOffersForProvider(
            @Param("providerId") UUID providerId,
            @Param("now") LocalDateTime now,
            @Param("searchingStatus") BookingStatus searchingStatus);
}
