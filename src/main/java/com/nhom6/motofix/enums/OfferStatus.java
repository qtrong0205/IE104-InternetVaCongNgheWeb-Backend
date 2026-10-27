package com.nhom6.motofix.enums;

public enum OfferStatus {
    PENDING,
    ACCEPTED,  // Thợ nhận yêu cầu và gửi giá dự kiến, thời gian đến
    DECLINED,  // Thợ từ chối yêu cầu
    EXPIRED    // Hết hạn, thợ không phản hồi kịp hoặc khách đã chọn thợ khác
}