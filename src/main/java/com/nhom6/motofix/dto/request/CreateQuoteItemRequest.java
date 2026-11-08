package com.nhom6.motofix.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nhom6.motofix.enums.QuoteItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateQuoteItemRequest(
        @NotNull(message = "item_type is required (LABOR or PART)")
        @JsonProperty("item_type")
        @JsonAlias({"itemType", "item_type"})
        QuoteItemType itemType,

        @NotBlank(message = "name is required")
        String name,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "unit_price is required")
        @DecimalMin(value = "0.0", message = "unit_price must be >= 0")
        @JsonProperty("unit_price")
        @JsonAlias({"unitPrice", "unit_price"})
        BigDecimal unitPrice,

        String description
) {
}

