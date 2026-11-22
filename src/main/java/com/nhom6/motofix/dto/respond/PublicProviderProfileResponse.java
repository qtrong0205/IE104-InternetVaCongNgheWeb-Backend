package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProviderProfileResponse(
        UUID id,
        String businessName,
        String address,
        Float baseLat,
        Float baseLng,
        Float serviceRadiusKm,
        Float ratingAvg,
        int ratingCount
) {
}
