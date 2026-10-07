package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.RejectProviderRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProviderController {

    private final ProviderService providerService;

    @GetMapping("/pending")
    public ResponseEntity<Page<ProviderProfileResponse>> getPendingProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                providerService.getPendingProviders(pageable)
        );
    }

    @PatchMapping("/{providerId}/approve")
    public ResponseEntity<ProviderProfileResponse> approveProvider(
            @PathVariable UUID providerId
    ) {
        return ResponseEntity.ok(
                providerService.approveProvider(providerId)
        );
    }

    @PatchMapping("/{providerId}/reject")
    public ResponseEntity<ProviderProfileResponse> rejectProvider(
            @PathVariable UUID providerId,
            @Valid @RequestBody RejectProviderRequest request
    ) {
        return ResponseEntity.ok(
                providerService.rejectProvider(
                        providerId,
                        request.reason()
                )
        );
    }
}