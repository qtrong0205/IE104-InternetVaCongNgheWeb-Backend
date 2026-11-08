package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.request.CancelBookingRequest;
import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.request.CreateQuoteRequest;
import com.nhom6.motofix.dto.request.DeclineOfferRequest;
import com.nhom6.motofix.dto.respond.BookingOfferSummaryResponse;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.OfferResultResponse;
import com.nhom6.motofix.dto.respond.QuoteResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.OfferService;
import com.nhom6.motofix.service.QuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;
    private final OfferService offerService;
    private final QuoteService quoteService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bookingService.createBooking(request, user.getId()));
    }

    @PostMapping("/{offerId}/offer/accept")
    public ResponseEntity<OfferResultResponse> acceptOffer(
            @PathVariable UUID offerId,
            @Valid @RequestBody AcceptOfferRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(offerService.accept(offerId, request, user));
    }

    @PostMapping("/{offerId}/offer/reject")
    public ResponseEntity<OfferResultResponse> rejectOffer(
            @PathVariable UUID offerId,
            @Valid @RequestBody DeclineOfferRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(offerService.reject(offerId, request, user));
    }

    @GetMapping("/{id}/offers")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'USER')")
    public ResponseEntity<List<BookingOfferSummaryResponse>> getOffers(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.getAcceptedOffers(id, user.getId()));
    }

    @PostMapping("/{id}/offers/{offerId}/select")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'USER')")
    public ResponseEntity<BookingResponse> selectOffer(
            @PathVariable UUID id,
            @PathVariable UUID offerId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.selectOffer(id, offerId, user.getId()));
    }

    @PatchMapping("/{id}/en-route")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<BookingResponse> confirmEnRoute(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.confirmEnRoute(id, user));
    }

    @PatchMapping("/{id}/arrived")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<BookingResponse> confirmArrived(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.confirmArrived(id, user));
    }

    @PostMapping("/{id}/quotes")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<QuoteResponse> createInitialQuote(
            @PathVariable UUID id,
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(quoteService.createInitialQuote(id, request, user));
    }

    /**
     * API 1: CUSTOMER chấp nhận báo giá chẩn đoán.
     * POST /api/bookings/{id}/quotes/{quoteId}/accept
     */
    @PostMapping("/{id}/quotes/{quoteId}/accept")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'USER')")
    public ResponseEntity<QuoteResponse> acceptQuote(
            @PathVariable UUID id,
            @PathVariable UUID quoteId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(quoteService.acceptQuote(id, quoteId, user));
    }

    /**
     * API 2: CUSTOMER hoặc PROVIDER hủy đơn đặt sửa chữa.
     * POST /api/bookings/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'USER', 'PROVIDER')")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelBookingRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(id, request, user));
    }

    /**
     * API 3: PROVIDER gửi báo giá chẩn đoán đã điều chỉnh (version mới).
     * POST /api/bookings/{id}/quotes/{quoteId}/revise
     */
    @PostMapping("/{id}/quotes/{quoteId}/revise")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<QuoteResponse> reviseQuote(
            @PathVariable UUID id,
            @PathVariable UUID quoteId,
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(quoteService.reviseQuote(id, quoteId, request, user));
    }
}
