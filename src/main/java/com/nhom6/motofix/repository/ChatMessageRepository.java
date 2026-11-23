package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query(value = "SELECT cm FROM ChatMessage cm JOIN FETCH cm.sender WHERE cm.booking.id = :bookingId ORDER BY cm.createdAt ASC",
           countQuery = "SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.booking.id = :bookingId")
    Page<ChatMessage> findByBookingIdWithSender(@Param("bookingId") UUID bookingId, Pageable pageable);

    @Query("SELECT cm FROM ChatMessage cm JOIN FETCH cm.sender WHERE cm.booking.id = :bookingId ORDER BY cm.createdAt ASC")
    List<ChatMessage> findByBookingIdWithSender(@Param("bookingId") UUID bookingId);
}
