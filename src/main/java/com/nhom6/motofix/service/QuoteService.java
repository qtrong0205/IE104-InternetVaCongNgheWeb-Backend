package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateQuoteItemRequest;
import com.nhom6.motofix.dto.request.CreateQuoteRequest;
import com.nhom6.motofix.dto.respond.QuoteResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.entity.RepairQuoteItem;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.QuoteItemStatus;
import com.nhom6.motofix.enums.QuoteStatus;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.RepairQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {
    private final BookingRepository bookingRepository;
    private final RepairQuoteRepository quoteRepository;

    @Transactional
    public QuoteResponse createInitialQuote(UUID bookingId, CreateQuoteRequest request, User user) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền sở hữu: Chỉ provider được chọn của booking mới được thao tác
        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        // Kiểm tra trạng thái: Phải ở trạng thái INSPECTING
        if (booking.getStatus() != BookingStatus.INSPECTING) {
            throw new ConflictException("Booking must be in INSPECTING status to create initial quote, current status is " + booking.getStatus());
        }

        // Không cho phép tạo nhiều chẩn đoán phiên bản 1 cho cùng một booking
        if (quoteRepository.existsByBookingIdAndVersion(bookingId, 1)) {
            throw new ConflictException("Initial quote (version 1) already exists for this booking");
        }

        RepairQuote quote = new RepairQuote();
        quote.setBooking(booking);
        quote.setVersion(1);
        quote.setBasedOnQuote(null);
        quote.setDiagnosisNote(request.diagnosisNote());
        quote.setStatus(QuoteStatus.PENDING);
        quote.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateQuoteItemRequest itemReq : request.items()) {
            RepairQuoteItem item = new RepairQuoteItem();
            item.setItemType(itemReq.itemType());
            item.setName(itemReq.name());
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(itemReq.unitPrice());
            item.setDescription(itemReq.description());

            BigDecimal itemAmount = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            item.setAmount(itemAmount);
            item.setStatus(QuoteItemStatus.PENDING);
            item.setCreatedAt(LocalDateTime.now());

            quote.addItem(item);
            totalAmount = totalAmount.add(itemAmount);
        }
        quote.setTotalAmount(totalAmount);

        RepairQuote saved = quoteRepository.save(quote);
        log.info("Provider {} created quote v1 for booking {}, total amount: {}", user.getId(), bookingId, totalAmount);

        return QuoteResponse.from(saved);
    }
}

