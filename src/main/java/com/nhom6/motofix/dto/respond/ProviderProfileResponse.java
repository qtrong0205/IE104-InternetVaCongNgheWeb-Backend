package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.enums.ApprovalStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProviderProfileResponse(
        UUID id,
        String businessName,
        String idDocumentUrl,
        String businessLicenseUrl,
        Float baseLat,
        Float baseLng,
        Float serviceRadiusKm,
        ApprovalStatus approvalStatus,
        String rejectionReason,
        LocalDateTime createdAt
) {
}