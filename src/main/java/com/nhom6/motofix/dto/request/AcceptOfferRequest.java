package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AcceptOfferRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = 0)
        BigDecimal estimatedMin,

        @NotNull @Positive @Digits(integer = 12, fraction = 0)
        BigDecimal estimatedMax,

        @NotNull @Positive
        Integer estimatedArrivalMinutes,

        @Size(max = 500)
        String note
) {}