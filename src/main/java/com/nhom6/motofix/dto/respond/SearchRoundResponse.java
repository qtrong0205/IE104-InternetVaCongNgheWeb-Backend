package com.nhom6.motofix.dto.respond;

public record SearchRoundResponse(
        Integer round,
        Double radiusKm,
        Integer candidatesCount,
        String message
) {
}
