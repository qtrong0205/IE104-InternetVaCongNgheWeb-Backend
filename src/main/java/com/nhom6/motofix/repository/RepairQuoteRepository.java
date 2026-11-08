package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.enums.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairQuoteRepository extends JpaRepository<RepairQuote, UUID> {
    boolean existsByBookingIdAndVersion(UUID bookingId, Integer version);

    List<RepairQuote> findByBookingIdOrderByVersionAsc(UUID bookingId);

    Optional<RepairQuote> findByBookingIdAndVersion(UUID bookingId, Integer version);

    @Query("SELECT COALESCE(MAX(q.version), 0) FROM RepairQuote q WHERE q.booking.id = :bookingId")
    Integer findMaxVersionByBookingId(@Param("bookingId") UUID bookingId);

    List<RepairQuote> findByBookingIdAndStatus(UUID bookingId, QuoteStatus status);

    boolean existsByBookingIdAndStatus(UUID bookingId, QuoteStatus status);
}

