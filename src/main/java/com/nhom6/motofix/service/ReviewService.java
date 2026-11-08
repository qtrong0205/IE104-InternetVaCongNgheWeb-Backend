package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateReviewRequest;
import com.nhom6.motofix.dto.respond.ReviewResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Review;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final BookingRepository bookingRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ReviewRepository reviewRepository;

    /**
     * API 3: CUSTOMER đánh giá thợ sau khi đơn hoàn tất.
     * POST /api/bookings/{id}/review
     * Điều kiện nghiệp vụ:
     * - Booking phải tồn tại.
     * - Người gọi phải là khách hàng sở hữu booking (booking.customer_id).
     * - Booking bắt buộc phải ở trạng thái COMPLETED.
     * - Mỗi booking chỉ được đánh giá duy nhất 1 lần (reviews.booking_id UNIQUE).
     * - Điểm đánh giá rating từ 1 đến 5.
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

        ProviderProfile provider = booking.getProvider();
        if (provider == null) {
            throw new ConflictException("Booking has no assigned provider to review");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setProvider(provider);
        review.setRating(request.rating());
        review.setComment(request.comment());

        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Xử lý xung đột unique constraint booking_id tại tầng CSDL nếu xảy ra race condition
            log.warn("Duplicate review attempt for booking {}", bookingId, e);
            throw new ConflictException("This booking has already been reviewed");
        }

        // Tính toán lại tổng số lượt đánh giá và điểm trung bình của thợ
        int newRatingCount = reviewRepository.countByProviderId(provider.getId());
        Double avgRating = reviewRepository.calculateAvgRatingByProviderId(provider.getId());

        provider.setRatingCount(newRatingCount);
        if (avgRating != null) {
            // Làm tròn điểm đánh giá trung bình đến 1 chữ số thập phân (ví dụ: 4.67 -> 4.7)
            float roundedAvg = Math.round(avgRating * 10.0f) / 10.0f;
            provider.setRatingAvg(roundedAvg);
        }
        providerProfileRepository.save(provider);

        log.info("Customer {} reviewed provider {} for booking {} with rating {}. New avg: {}, count: {}",
                customer.getId(), provider.getId(), bookingId, request.rating(), provider.getRatingAvg(), newRatingCount);

        return ReviewResponse.from(review);
    }
}

