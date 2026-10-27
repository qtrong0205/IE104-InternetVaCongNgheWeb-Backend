package com.nhom6.motofix.entity;

import com.nhom6.motofix.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "estimated_min", precision = 12)
    private BigDecimal estimatedMin;

    @Column(name = "estimated_max", precision = 12)
    private BigDecimal estimatedMax;

    @Column(name = "estimated_arrival_minutes")
    private Integer estimatedArrivalMinutes;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "distance_m")
    private Double distanceM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderProfile provider;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = createdAt.plusMinutes(10);
        }
    }
}