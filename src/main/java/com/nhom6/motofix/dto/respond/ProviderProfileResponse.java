package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nhom6.motofix.enums.ApprovalStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProviderProfileResponse(
        UUID id,
        String businessName,
        String idDocumentUrl,
        String businessLicenseUrl,
        String address,
        Float baseLat,
        Float baseLng,
        Float serviceRadiusKm,
        ApprovalStatus approvalStatus,
        String rejectionReason,
        @JsonProperty("isActive")
        boolean isActive,
        Float ratingAvg,
        int ratingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    @JsonProperty("is_active")
    public boolean getIsActiveSnake() {
        return isActive;
    }
}
