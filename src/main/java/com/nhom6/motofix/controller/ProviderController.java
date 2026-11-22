package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.ProviderActiveRequest;
import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.IncomingOfferResponse;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.dto.respond.PublicProviderProfileResponse;
import com.nhom6.motofix.dto.respond.ProviderRevenueResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    private final ProviderService providerService;

    @PostMapping("/register")
    public ResponseEntity<ProviderProfileResponse> uploadProfile(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid ProviderProfileRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(providerService.uploadProfile(user, request));
    }

    @GetMapping("/me")
    public ResponseEntity<ProviderProfileResponse> getMyProfile(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(providerService.getMyProfile(user));
    }

    @GetMapping("/{providerId}")
    public ResponseEntity<PublicProviderProfileResponse> getPublicProfile(
            @PathVariable UUID providerId
    ) {
        return ResponseEntity.ok(providerService.getPublicProfile(providerId));
    }

    @PutMapping("/me")
    public ResponseEntity<ProviderProfileResponse> updateMyProfile(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid ProviderProfileRequest request
    ) {
        return ResponseEntity.ok(providerService.updateMyProfile(user, request));
    }

    @PatchMapping({"/me/active", "/me/online"})
    public ResponseEntity<ProviderProfileResponse> updateActiveStatus(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid ProviderActiveRequest request
    ) {
        return ResponseEntity.ok(providerService.updateActiveStatus(user, request));
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Page<BookingResponse>> getProviderBookings(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(providerService.getProviderBookings(user, status, pageable));
    }

    @GetMapping("/me/revenue")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<ProviderRevenueResponse> getMyRevenue(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(providerService.getProviderRevenue(user, date));
    }

    @GetMapping("/incoming-offers")
    public ResponseEntity<List<IncomingOfferResponse>> getIncomingOffers(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(providerService.getIncomingOffers(user));
    }
}
