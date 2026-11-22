package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateReviewRequest;
import com.nhom6.motofix.dto.respond.ReviewResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Review;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final BookingRepository bookingRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ReviewRepository reviewRepository;

    private Clock clock = Clock.systemDefaultZone();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    /**
     * API 3: CUSTOMER đánh giá thợ sau khi đơn hoàn tất.
     * POST /api/bookings/{id}/review
     * Điều kiện nghiệp vụ:
     * - Booking phải tồn tại.
     * - Người gọi phải là khách hàng sở hữu booking (booking.customer_id).
     * - Booking bắt buộc phải ở trạng thái COMPLETED.
     * - Mỗi booking chỉ được đánh giá duy nhất 1 lần (reviews.booking_id UNIQUE).
     * - Điểm đánh giá rating từ 1 đến 5.
     * - Trong thời hạn 168 giờ (7 ngày) kể từ khi thợ xác nhận nhận tiền (completedAt).
     * Kết quả:
     * - Lưu bản ghi Review.
     * - Cập nhật lại rating_count và rating_avg trong ProviderProfile của thợ.
     */
    @Transactional
    public ReviewResponse createReview(UUID bookingId, CreateReviewRequest request, User customer) {
        // Khóa bi quan (pessimistic lock) trên dòng Booking để ngăn chặn request đánh giá đồng thời
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền: Chỉ khách hàng sở hữu booking mới được phép đánh giá
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("You are not the customer of this booking");
        }

        // Kiểm tra trạng thái booking: Bắt buộc phải là COMPLETED
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException("Booking must be COMPLETED before reviewing, currently: " + booking.getStatus());
        }

        // Kiểm tra booking đã từng được đánh giá hay chưa
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ConflictException("This booking has already been reviewed");
        }

        // Kiểm tra rating hợp lệ từ 1 đến 5 sao
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new BadRequestException("Điểm đánh giá (rating) phải từ 1 đến 5");
        }

        // Kiểm tra thời hạn đánh giá: tối đa 168 giờ (7 ngày) kể từ khi hoàn tất nhận tiền
        LocalDateTime completedAt = booking.getCompletedAt();
        if (completedAt == null) {
            completedAt = booking.getUpdatedAt() != null ? booking.getUpdatedAt() : booking.getCreatedAt();
        }
        LocalDateTime deadline = completedAt.plusHours(168);
        LocalDateTime now = LocalDateTime.now(clock);
        // Từ đúng thời điểm hết hạn (now >= deadline) thì đóng quyền gửi
        if (!now.isBefore(deadline)) {
            throw new ConflictException("Thời hạn đánh giá cho đơn cứu hộ đã kết thúc (tối đa 168 giờ sau khi hoàn tất thanh toán)");
        }

        ProviderProfile provider = booking.getProvider();
        if (provider == null) {
            throw new ConflictException("Booking has no assigned provider to review");
        }

        // Khóa bi quan trên ProviderProfile để bảo đảm tính đúng ratingCount và ratingAvg khi nhiều khách review đồng thời
        ProviderProfile lockedProvider = providerProfileRepository.findByIdWithLock(provider.getId())
                .orElse(provider);

        Review review = new Review();
        review.setBooking(booking);
        review.setProvider(lockedProvider);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setCreatedAt(now);

        try {
            review = reviewRepository.saveAndFlush(review);
            booking.setReview(review);
        } catch (DataIntegrityViolationException e) {
            // Xử lý xung đột unique constraint booking_id tại tầng CSDL nếu xảy ra race condition
            log.warn("Duplicate review attempt for booking {}", bookingId, e);
            throw new ConflictException("This booking has already been reviewed");
        }

        // Tính toán lại tổng số lượt đánh giá và điểm trung bình của thợ
        int newRatingCount = reviewRepository.countByProviderId(lockedProvider.getId());
        Double avgRating = reviewRepository.calculateAvgRatingByProviderId(lockedProvider.getId());

        lockedProvider.setRatingCount(newRatingCount);
        if (avgRating != null) {
            // Làm tròn điểm đánh giá trung bình đến 1 chữ số thập phân (ví dụ: 4.67 -> 4.7)
            float roundedAvg = Math.round(avgRating * 10.0f) / 10.0f;
            lockedProvider.setRatingAvg(roundedAvg);
        }
        providerProfileRepository.save(lockedProvider);

        log.info("Customer {} reviewed provider {} for booking {} with rating {}. New avg: {}, count: {}",
                customer.getId(), lockedProvider.getId(), bookingId, request.rating(), lockedProvider.getRatingAvg(), newRatingCount);

        return ReviewResponse.from(review);
    }

    /**
     * Xem danh sách các đánh giá của một thợ (Provider).
     * Endpoint: GET /api/providers/{providerId}/reviews
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getProviderReviews(UUID providerId) {
        if (!providerProfileRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Hồ sơ thợ không tồn tại");
        }
        return reviewRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }
}

