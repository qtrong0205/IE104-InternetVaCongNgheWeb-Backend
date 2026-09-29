package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    private final ProviderService providerService;

    @PostMapping("/register")
    public ProviderProfileResponse uploadProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ProviderProfileRequest request
            ){
        return providerService.uploadProfile(jwt, request);
    }
}
