-- ==============================================================================
-- MotoFix DB Migration V1.6.0
-- Đợt BE08: Tạo bảng chat_messages lưu trữ lịch sử tin nhắn trò chuyện theo booking
-- ==============================================================================

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_booking_created ON chat_messages(booking_id, created_at ASC);
