package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.NotNull;

public record ProviderProfileRequest(
        String businessName,

        @NotNull
        String idDocumentUrl,

        String businessLicenseUrl,

        @NotNull
        Float baseLat,

        @NotNull
        Float baseLng,
        Float serviceRadiusKm
) {
}
