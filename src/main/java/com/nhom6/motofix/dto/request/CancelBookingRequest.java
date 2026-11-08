package com.nhom6.motofix.dto.request;

/**
 * DTO nhận yêu cầu hủy đơn từ khách hàng hoặc thợ
 */
public record CancelBookingRequest(
        String reason
) {
}

