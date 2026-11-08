package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.RepairQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepairQuoteRepository extends JpaRepository<RepairQuote, UUID> {
    boolean existsByBookingIdAndVersion(UUID bookingId, Integer version);

    List<RepairQuote> findByBookingIdOrderByVersionAsc(UUID bookingId);

    Optional<RepairQuote> findByBookingIdAndVersion(UUID bookingId, Integer version);
}

