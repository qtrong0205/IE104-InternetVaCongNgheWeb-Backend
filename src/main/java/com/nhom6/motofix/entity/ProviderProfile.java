package com.nhom6.motofix.entity;

import com.nhom6.motofix.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "id_document_url")
    private String idDocumentUrl;

    @Column(name = "business_license_url")
    private String businessLicenseUrl;

    @Column(name = "base_lat")
    private Float baseLat;

    @Column(name = "base_lng")
    private Float baseLng;

    @Column(name = "service_radius_km")
    private Float serviceRadiusKm;

    @Column(name = "approval_status")
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "is_active")
    private boolean isActive = false;

    @Column(name = "rating_avg")
    private Float ratingAvg;

    @Column(name = "rating_count")
    private int ratingCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
