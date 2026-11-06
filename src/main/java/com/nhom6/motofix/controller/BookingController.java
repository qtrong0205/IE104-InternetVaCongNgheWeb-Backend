package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.request.DeclineOfferRequest;
import com.nhom6.motofix.dto.respond.BookingOfferSummaryResponse;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.OfferResultResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.OfferService;
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
}
