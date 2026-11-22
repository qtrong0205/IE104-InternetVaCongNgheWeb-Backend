package com.nhom6.motofix.entity;

import com.nhom6.motofix.enums.QuoteItemStatus;
import com.nhom6.motofix.enums.QuoteItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repair_quote_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepairQuoteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private RepairQuote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private QuoteItemType itemType;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteItemStatus status = QuoteItemStatus.PENDING;

    /**
     * Lấy trạng thái hiệu lực của item từ Quote chứa nó theo đặc tả:
     * "Mỗi item lấy trạng thái hiệu lực từ quote chứa nó. Bỏ status riêng của repair_quote_items khỏi mô hình đích, DTO và logic tạo/duyệt quote."
     */
    public QuoteItemStatus getEffectiveStatus() {
        if (this.quote != null && this.quote.getStatus() != null) {
            return switch (this.quote.getStatus()) {
                case ACCEPTED -> QuoteItemStatus.ACCEPTED;
                case PENDING -> QuoteItemStatus.PENDING;
                case CANCELLED, DECLINED -> QuoteItemStatus.CANCELLED;
                default -> QuoteItemStatus.PENDING;
            };
        }
        return this.status != null ? this.status : QuoteItemStatus.PENDING;
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (quantity == null) {
            quantity = 1;
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }
        this.amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

