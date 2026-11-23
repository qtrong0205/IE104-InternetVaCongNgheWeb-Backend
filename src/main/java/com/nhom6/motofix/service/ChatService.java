package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.SendMessageRequest;
import com.nhom6.motofix.dto.respond.ChatMessageResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ChatMessage;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final BookingRepository bookingRepository;
    private final WebSocketSyncService webSocketSyncService;

    @Transactional
    public ChatMessageResponse sendMessage(UUID bookingId, SendMessageRequest request, User user) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để gửi tin nhắn");
        }

        if (request == null || request.message() == null || request.message().trim().isEmpty()) {
            throw new BadRequestException("Nội dung tin nhắn không được để trống");
        }

        String trimmedMessage = request.message().trim();
        if (trimmedMessage.length() > 1000) {
            throw new BadRequestException("Nội dung tin nhắn không được vượt quá 1000 ký tự");
        }

        Booking booking = bookingRepository.findByIdWithCustomerAndProvider(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        // 1. Quyền tham gia: Chỉ khách chủ đơn hoặc thợ được chọn
        boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
        boolean isAssignedProvider = booking.getProvider() != null
                && booking.getProvider().getUser() != null
                && booking.getProvider().getUser().getId().equals(user.getId());

        if (!isCustomer && !isAssignedProvider) {
            log.warn("User {} is not authorized to chat for booking {}", user.getId(), bookingId);
            throw new AccessDeniedException("Bạn không có quyền tham gia trò chuyện trong đơn này");
        }

        // 2. Kiểm tra trạng thái booking:
        // Đang tìm thợ (SEARCHING): Chưa chọn thợ thì không được phép chat (kể cả khi thợ đã ACCEPTED offer)
        if (booking.getStatus() == BookingStatus.SEARCHING) {
            throw new ConflictException("Chưa thể trò chuyện khi đơn đang ở trạng thái tìm thợ. Khách hàng cần chọn thợ trước.");
        }

        // Đơn đã kết thúc (COMPLETED / CANCELLED): Lịch sử chuyển sang chỉ đọc, backend chặn gửi tin nhắn mới (chống gửi từ tab cũ sau hủy)
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Đơn cứu hộ đã kết thúc (" + booking.getStatus() + "). Cuộc trò chuyện đã chuyển sang chế độ chỉ đọc.");
        }

        // 3. Tạo và lưu tin nhắn
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setBooking(booking);
        chatMessage.setSender(user);
        chatMessage.setMessage(trimmedMessage);
        chatMessage.setCreatedAt(LocalDateTime.now());

        ChatMessage saved = chatMessageRepository.save(chatMessage);
        log.info("ChatMessage saved for booking {}: sender={}, role={}", bookingId, user.getId(), user.getCurrentRole());

        ChatMessageResponse response = toResponse(saved);

        // 4. Phát sự kiện WebSocket theo thời gian thực
        webSocketSyncService.notifyChatMessage(booking, response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getChatHistory(UUID bookingId, User user, Pageable pageable) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để xem lịch sử trò chuyện");
        }

        Booking booking = bookingRepository.findByIdWithCustomerAndProvider(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
        boolean isAssignedProvider = booking.getProvider() != null
                && booking.getProvider().getUser() != null
                && booking.getProvider().getUser().getId().equals(user.getId());

        if (!isCustomer && !isAssignedProvider) {
            log.warn("User {} is not authorized to read chat for booking {}", user.getId(), bookingId);
            throw new AccessDeniedException("Bạn không có quyền xem lịch sử trò chuyện của đơn này");
        }

        if (booking.getStatus() == BookingStatus.SEARCHING) {
            throw new ConflictException("Cuộc trò chuyện chưa được mở khi đơn đang ở trạng thái tìm thợ");
        }

        return chatMessageRepository.findByBookingIdWithSender(bookingId, pageable)
                .map(this::toResponse);
    }

    private ChatMessageResponse toResponse(ChatMessage msg) {
        User sender = msg.getSender();
        return new ChatMessageResponse(
                msg.getId(),
                msg.getBooking().getId(),
                sender.getId(),
                sender.getFullName(),
                sender.getCurrentRole(),
                msg.getMessage(),
                msg.getCreatedAt()
        );
    }
}
