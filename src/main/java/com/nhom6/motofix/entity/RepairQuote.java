package com.nhom6.motofix.entity;

import com.nhom6.motofix.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repair_quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepairQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "labor_cost", nullable = false, precision = 12, scale = 0)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "parts_cost", nullable = false, precision = 12, scale = 0)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.PENDING_CUSTOMER;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        calculateTotal();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateTotal();
    }

    private void calculateTotal() {
        this.totalAmount = laborCost.add(partsCost);
    }
}
