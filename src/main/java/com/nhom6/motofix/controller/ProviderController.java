package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    private final ProviderService providerService;

    @PostMapping("/register")
    public ResponseEntity<ProviderProfileResponse> uploadProfile(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid ProviderProfileRequest request
            ){
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(providerService.uploadProfile(user, request));
    }
}
