package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END FROM Booking b WHERE b.provider.id = :providerId AND b.status IN :statuses")
    boolean hasActiveBookings(@Param("providerId") UUID providerId, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.customer.id = :customerId AND b.status IN :statuses ORDER BY b.createdAt DESC")
    List<Booking> findActiveBookingsByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.status IN :statuses ORDER BY b.createdAt DESC")
    List<Booking> findActiveBookingsByProviderId(
            @Param("providerId") UUID providerId,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.status IN :statuses")
    Page<Booking> findByProviderIdAndStatusIn(
            @Param("providerId") UUID providerId,
            @Param("statuses") Collection<BookingStatus> statuses,
            Pageable pageable
    );

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.status = :status")
    Page<Booking> findByProviderIdAndStatus(
            @Param("providerId") UUID providerId,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    @Query("SELECT b FROM Booking b WHERE b.customer.id = :customerId AND b.status IN :statuses")
    Page<Booking> findByCustomerIdAndStatusIn(
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<BookingStatus> statuses,
            Pageable pageable
    );

    @Query("SELECT b FROM Booking b WHERE b.customer.id = :customerId AND b.status = :status")
    Page<Booking> findByCustomerIdAndStatus(
            @Param("customerId") UUID customerId,
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.quotes q WHERE b.provider.id = :providerId AND b.status = :status AND COALESCE(b.completedAt, b.updatedAt, b.createdAt) >= :startOfDay AND COALESCE(b.completedAt, b.updatedAt, b.createdAt) <= :endOfDay")
    List<Booking> findCompletedBookingsWithQuotesByProviderAndDateRange(
            @Param("providerId") UUID providerId,
            @Param("status") BookingStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
