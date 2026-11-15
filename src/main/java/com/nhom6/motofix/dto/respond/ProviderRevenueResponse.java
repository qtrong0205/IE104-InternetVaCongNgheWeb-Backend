package com.nhom6.motofix.dto.respond;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProviderRevenueResponse(
        LocalDate date,
        BigDecimal totalRevenue,
        long totalBookings,
        List<BookingResponse> bookings
) {
}

