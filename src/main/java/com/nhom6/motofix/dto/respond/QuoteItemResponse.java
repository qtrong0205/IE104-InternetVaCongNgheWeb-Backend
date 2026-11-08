package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.entity.RepairQuoteItem;
import com.nhom6.motofix.enums.QuoteItemStatus;
import com.nhom6.motofix.enums.QuoteItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record QuoteItemResponse(
        UUID id,
        UUID itemId,
        QuoteItemType itemType,
        String name,
        Integer quantity,
        BigDecimal unitPrice,
        String description,
        BigDecimal amount,
        QuoteItemStatus status,
        LocalDateTime createdAt
) {
    public static QuoteItemResponse from(RepairQuoteItem item) {
        return new QuoteItemResponse(
                item.getId(),
                item.getId(),
                item.getItemType(),
                item.getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDescription(),
                item.getAmount(),
                item.getStatus(),
                item.getCreatedAt()
        );
    }
}

