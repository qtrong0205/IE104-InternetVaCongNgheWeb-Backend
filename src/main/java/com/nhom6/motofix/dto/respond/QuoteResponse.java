package com.nhom6.motofix.dto.respond;

import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.enums.QuoteStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        UUID quoteId,
        UUID bookingId,
        Integer version,
        UUID basedOnQuoteId,
        String diagnosisNote,
        BigDecimal totalAmount,
        QuoteStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        List<QuoteItemResponse> items
) {
    public static QuoteResponse from(RepairQuote quote) {
        return new QuoteResponse(
                quote.getId(),
                quote.getId(),
                quote.getBooking().getId(),
                quote.getVersion(),
                quote.getBasedOnQuote() != null ? quote.getBasedOnQuote().getId() : null,
                quote.getDiagnosisNote(),
                quote.getTotalAmount(),
                quote.getStatus(),
                quote.getCreatedAt(),
                quote.getRespondedAt(),
                quote.getItems() != null
                        ? quote.getItems().stream().map(QuoteItemResponse::from).toList()
                        : List.of()
        );
    }
}

