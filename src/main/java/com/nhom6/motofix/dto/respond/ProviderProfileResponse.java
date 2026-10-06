package com.nhom6.motofix.dto.respond;

public record ProviderProfileResponse(
        String businessName,
        String idDocumentUrl,
        String businessLicenseUrl,
        Float baseLat,
        Float baseLng,
        Float serviceRadiusKm
) {
}
