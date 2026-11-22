package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderProfileRequest(
        @JsonAlias({"business_name", "businessName"})
        String businessName,

        @NotBlank(message = "idDocumentUrl không được để trống")
        @JsonAlias({"id_document_url", "idDocumentUrl"})
        String idDocumentUrl,

        @JsonAlias({"business_license_url", "businessLicenseUrl"})
        String businessLicenseUrl,

        @JsonAlias({"address"})
        String address,

        @NotNull(message = "baseLat không được để trống")
        @JsonAlias({"base_lat", "baseLat"})
        Float baseLat,

        @NotNull(message = "baseLng không được để trống")
        @JsonAlias({"base_lng", "baseLng"})
        Float baseLng,

        @JsonAlias({"service_radius_km", "serviceRadiusKm"})
        Float serviceRadiusKm
) {
    public ProviderProfileRequest(
            String businessName,
            String idDocumentUrl,
            String businessLicenseUrl,
            Float baseLat,
            Float baseLng,
            Float serviceRadiusKm
    ) {
        this(businessName, idDocumentUrl, businessLicenseUrl, null, baseLat, baseLng, serviceRadiusKm);
    }
}

