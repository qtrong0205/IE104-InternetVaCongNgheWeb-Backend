package com.nhom6.motofix.dto.request;

import jakarta.validation.constraints.Size;

public record DeclineOfferRequest(
        @Size(max = 500)
        String note
) {}