package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.ConfirmPaymentRequest;
import com.nhom6.motofix.dto.request.CreateReviewRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.ProviderRevenueResponse;
import com.nhom6.motofix.dto.respond.ReviewResponse;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RepairQuoteRepository;
import com.nhom6.motofix.repository.ReviewRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.ProviderService;
import com.nhom6.motofix.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class BE06PaymentRevenueAndReviewWorkflowTests {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private RepairQuoteRepository quoteRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ProviderService providerService;

    private User customer;
    private User customer2;
    private User strangerCustomer;
    private User providerUser;
    private ProviderProfile providerProfile;
    private Booking booking;

    private final ZoneId zoneId = ZoneId.systemDefault();

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setFullName("Nguyen Van Khach");
        customer.setEmail("khach_" + UUID.randomUUID() + "@test.com");
        customer.setPhone("098" + System.currentTimeMillis() % 10000000);
        customer.setPasswordHash("hash");
        customer.setStatus("active");
        customer = userRepository.save(customer);

        customer2 = new User();
        customer2.setFullName("Tran Thi Khach 2");
        customer2.setEmail("khach2_" + UUID.randomUUID() + "@test.com");
        customer2.setPhone("097" + System.currentTimeMillis() % 10000000);
        customer2.setPasswordHash("hash");
        customer2.setStatus("active");
        customer2 = userRepository.save(customer2);

        strangerCustomer = new User();
        strangerCustomer.setFullName("Le Van La");
        strangerCustomer.setEmail("stranger_" + UUID.randomUUID() + "@test.com");
        strangerCustomer.setPhone("096" + System.currentTimeMillis() % 10000000);
        strangerCustomer.setPasswordHash("hash");
        strangerCustomer.setStatus("active");
        strangerCustomer = userRepository.save(strangerCustomer);

        providerUser = new User();
        providerUser.setFullName("Pham Van Tho");
        providerUser.setEmail("tho_" + UUID.randomUUID() + "@test.com");
        providerUser.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser.setPasswordHash("hash");
        providerUser.setStatus("active");
        providerUser = userRepository.save(providerUser);

        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Garage MotoFix Quan 1");
        providerProfile.setAddress("123 Nguyen Hue, Quan 1, TP.HCM");
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfile.setRatingCount(0);
        providerProfile.setRatingAvg(null);
        providerProfile = providerProfileRepository.save(providerProfile);

        booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(providerProfile);
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        booking.setLatitude(10.776889);
        booking.setLongitude(106.700806);
        booking.setAddress("123 Nguyen Hue, Ben Nghe, Quan 1, TP.HCM");
        booking.setVehicleBrand("Honda");
        booking.setVehicleModel("Air Blade");
        booking.setVehicleLicensePlate("59-A1 12345");
        booking.setProblemDescription("Xe bi thung lop giua duong");
        booking.setCreatedAt(LocalDateTime.now().minusHours(2));
        booking = bookingRepository.save(booking);

        // Reset clock về giờ thực tế trước mỗi test
        bookingService.setClock(Clock.systemDefaultZone());
        reviewService.setClock(Clock.systemDefaultZone());
        providerService.setClock(Clock.systemDefaultZone());
    }

    private RepairQuote createQuote(Booking b, int version, QuoteStatus status, BigDecimal amount) {
        RepairQuote quote = new RepairQuote();
        quote.setBooking(b);
        quote.setVersion(version);
        quote.setStatus(status);
        quote.setDiagnosisNote("Sua xe v" + version);
        quote.setTotalAmount(amount);
        quote.setCreatedAt(LocalDateTime.now().minusHours(1));

        RepairQuoteItem item = new RepairQuoteItem();
        item.setName("Chi phi sua chua v" + version);
        item.setItemType(QuoteItemType.LABOR);
        item.setQuantity(1);
        item.setUnitPrice(amount);
        item.setAmount(amount);
        quote.addItem(item);

        RepairQuote savedQuote = quoteRepository.save(quote);
        if (b.getQuotes() == null) {
            b.setQuotes(new java.util.ArrayList<>());
        }
        b.getQuotes().add(savedQuote);
        return savedQuote;
    }

    // =========================================================================
    // 1. THANH TOÁN: Xác nhận nhận tiền, lưu completedAt một lần, chặn bấm lặp
    // =========================================================================

    @Test
    @DisplayName("BE06-01: Thợ xác nhận nhận tiền CASH thành công -> Chuyển COMPLETED, lưu mốc completedAt và paymentMethod")
    void testConfirmPayment_Success_RecordedOnce() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED, BigDecimal.valueOf(120000));

        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 22, 14, 30, 0);
        Clock fixedClock = Clock.fixed(fixedTime.atZone(zoneId).toInstant(), zoneId);
        bookingService.setClock(fixedClock);

        BookingResponse response = bookingService.confirmPayment(
                booking.getId(),
                new ConfirmPaymentRequest(PaymentMethod.CASH),
                providerUser
        );

        assertNotNull(response);
        assertEquals(BookingStatus.COMPLETED, response.status());
        assertEquals(PaymentMethod.CASH, response.paymentMethod());
        assertEquals(fixedTime, response.completedAt());
        assertNotNull(response.reviewDeadline());
        assertEquals(fixedTime.plusHours(168), response.reviewDeadline());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.COMPLETED, reloaded.getStatus());
        assertEquals(PaymentMethod.CASH, reloaded.getPaymentMethod());
        assertEquals(fixedTime, reloaded.getCompletedAt());
    }

    @Test
    @DisplayName("BE06-02: Bấm nhận tiền lặp lại (đã COMPLETED) -> Bị từ chối 409 Conflict")
    void testConfirmPayment_DuplicateCall_ThrowsConflict() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED, BigDecimal.valueOf(120000));

        // Lần 1: Thành công
        bookingService.confirmPayment(
                booking.getId(),
                new ConfirmPaymentRequest(PaymentMethod.BANK_TRANSFER),
                providerUser
        );

        // Lần 2: Bấm lặp -> Ném ConflictException
        ConflictException ex = assertThrows(ConflictException.class, () ->
                bookingService.confirmPayment(
                        booking.getId(),
                        new ConfirmPaymentRequest(PaymentMethod.BANK_TRANSFER),
                        providerUser
                )
        );
        assertTrue(ex.getMessage().contains("WAITING_PAYMENT"));
    }

    // =========================================================================
    // 2. DOANH THU: Tính theo ngày thực nhận (completedAt), quote ACCEPTED cuối, hủy không tính
    // =========================================================================

    @Test
    @DisplayName("BE06-03: Đơn tạo ngày A và trả tiền ngày B -> Doanh thu ghi nhận ở ngày B, ngày A = 0")
    void testRevenue_DifferentCreationAndPaymentDates() {
        LocalDate createdDate = LocalDate.of(2026, 11, 10);
        LocalDate paymentDate = LocalDate.of(2026, 11, 15);

        LocalDateTime createdDateTime = createdDate.atTime(8, 0, 0);
        LocalDateTime paymentDateTime = paymentDate.atTime(15, 0, 0);

        booking.setCreatedAt(createdDateTime);
        booking.setCompletedAt(paymentDateTime);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        booking = bookingRepository.saveAndFlush(booking);

        createQuote(booking, 1, QuoteStatus.ACCEPTED, BigDecimal.valueOf(250000));
        quoteRepository.flush();

        // Kiểm tra doanh thu ngày tạo (ngày 10)
        ProviderRevenueResponse revDayA = providerService.getProviderRevenue(providerUser, createdDate);
        assertNotNull(revDayA);
        assertEquals(BigDecimal.ZERO, revDayA.totalRevenue());
        assertEquals(0L, revDayA.totalBookings());

        // Kiểm tra doanh thu ngày thực nhận tiền (ngày 15)
        ProviderRevenueResponse revDayB = providerService.getProviderRevenue(providerUser, paymentDate);
        assertNotNull(revDayB);
        assertEquals(BigDecimal.valueOf(250000), revDayB.totalRevenue());
        assertEquals(1L, revDayB.totalBookings());
        assertEquals(1, revDayB.bookings().size());
        assertEquals(booking.getId(), revDayB.bookings().get(0).id());
    }

    @Test
    @DisplayName("BE06-04: Doanh thu tính theo quote ACCEPTED cuối cùng sau khi đổi quote (SUPERSEDED 100k, ACCEPTED 180k)")
    void testRevenue_LatestAcceptedQuoteAfterRevision() {
        LocalDate today = LocalDate.of(2026, 11, 22);
        LocalDateTime completedAt = today.atTime(10, 0, 0);

        booking.setCompletedAt(completedAt);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPaymentMethod(PaymentMethod.CASH);
        booking = bookingRepository.saveAndFlush(booking);

        // Quote v1 bị SUPERSEDED (100k)
        createQuote(booking, 1, QuoteStatus.SUPERSEDED, BigDecimal.valueOf(100000));
        // Quote v2 được ACCEPTED (180k)
        createQuote(booking, 2, QuoteStatus.ACCEPTED, BigDecimal.valueOf(180000));
        quoteRepository.flush();

        ProviderRevenueResponse revenueResponse = providerService.getProviderRevenue(providerUser, today);
        assertNotNull(revenueResponse);
        // Doanh thu phải là 180k, không bị cộng dồn thành 280k
        assertEquals(BigDecimal.valueOf(180000), revenueResponse.totalRevenue());
        assertEquals(1L, revenueResponse.totalBookings());
    }

    @Test
    @DisplayName("BE06-05: Đơn hủy (CANCELLED) không được tính vào doanh thu ngày")
    void testRevenue_CancelledBookingsExcluded() {
        LocalDate today = LocalDate.of(2026, 11, 22);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCompletedAt(null);
        bookingRepository.save(booking);

        createQuote(booking, 1, QuoteStatus.ACCEPTED, BigDecimal.valueOf(200000));

        ProviderRevenueResponse revenueResponse = providerService.getProviderRevenue(providerUser, today);
        assertNotNull(revenueResponse);
        assertEquals(BigDecimal.ZERO, revenueResponse.totalRevenue());
        assertEquals(0L, revenueResponse.totalBookings());
    }

    // =========================================================================
    // 3. ĐÁNH GIÁ (REVIEW): Kiểm soát thời hạn 168 giờ bằng Clock kiểm soát được
    // =========================================================================

    @Test
    @DisplayName("BE06-06: Review tại trước hạn (T + 168h - 1s) -> Thành công")
    void testReview_BeforeDeadline_Success() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 11, 1, 10, 0, 0);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(completedAt);
        bookingRepository.save(booking);

        // Đặt đồng hồ tại 168 giờ trừ 1 giây
        LocalDateTime testTime = completedAt.plusHours(168).minusSeconds(1);
        reviewService.setClock(Clock.fixed(testTime.atZone(zoneId).toInstant(), zoneId));

        CreateReviewRequest request = new CreateReviewRequest(5, "Dich vu tot!");
        ReviewResponse response = reviewService.createReview(booking.getId(), request, customer);

        assertNotNull(response);
        assertEquals(5, response.rating());
        assertEquals("Dich vu tot!", response.comment());
        assertEquals(customer.getFullName(), response.customerName());

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(1, updated.getRatingCount());
        assertEquals(5.0f, updated.getRatingAvg());
    }

    @Test
    @DisplayName("BE06-07: Review tại đúng hạn (T + 168h) -> Bị từ chối 409 Conflict")
    void testReview_ExactlyAtDeadline_Conflict() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 11, 1, 10, 0, 0);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(completedAt);
        bookingRepository.save(booking);

        // Đặt đồng hồ đúng tại T + 168h
        LocalDateTime testTime = completedAt.plusHours(168);
        reviewService.setClock(Clock.fixed(testTime.atZone(zoneId).toInstant(), zoneId));

        CreateReviewRequest request = new CreateReviewRequest(4, "Danh gia dung han");
        ConflictException ex = assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), request, customer)
        );
        assertTrue(ex.getMessage().contains("Thời hạn đánh giá"));
    }

    @Test
    @DisplayName("BE06-08: Review sau hạn (T + 168h + 1s) -> Bị từ chối 409 Conflict")
    void testReview_AfterDeadline_Conflict() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 11, 1, 10, 0, 0);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(completedAt);
        bookingRepository.save(booking);

        // Đặt đồng hồ sau hạn
        LocalDateTime testTime = completedAt.plusHours(168).plusSeconds(1);
        reviewService.setClock(Clock.fixed(testTime.atZone(zoneId).toInstant(), zoneId));

        CreateReviewRequest request = new CreateReviewRequest(5, "Danh gia tre");
        ConflictException ex = assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), request, customer)
        );
        assertTrue(ex.getMessage().contains("Thời hạn đánh giá"));
    }

    // =========================================================================
    // 4. KIỂM THỬ XUNG ĐỘT (CONCURRENCY) & QUYỀN TRÊN REVIEW
    // =========================================================================

    @Test
    @DisplayName("BE06-09: Hai tab đánh giá trùng trên cùng 1 booking -> Tab 1 thành công, Tab 2 ném 409 Conflict")
    void testReview_DuplicateTabs_SecondCallThrowsConflict() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now().minusHours(1));
        bookingRepository.save(booking);

        // Tab 1 gửi đánh giá thành công
        CreateReviewRequest req1 = new CreateReviewRequest(5, "Tuyet voi");
        ReviewResponse res1 = reviewService.createReview(booking.getId(), req1, customer);
        assertNotNull(res1);

        // Tab 2 gửi đánh giá cho cùng booking -> ConflictException
        CreateReviewRequest req2 = new CreateReviewRequest(4, "Gui trung");
        ConflictException ex = assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), req2, customer)
        );
        assertTrue(ex.getMessage().contains("already been reviewed"));
    }

    @Test
    @DisplayName("BE06-10: Hai khách khác nhau đánh giá cho cùng 1 thợ -> Thống kê rating_count và rating_avg chính xác")
    void testReview_MultipleCustomers_RatingStatsAccurate() {
        // Đơn 1 của customer 1
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now().minusHours(2));
        bookingRepository.save(booking);

        // Đơn 2 của customer 2
        Booking booking2 = new Booking();
        booking2.setCustomer(customer2);
        booking2.setProvider(providerProfile);
        booking2.setStatus(BookingStatus.COMPLETED);
        booking2.setCompletedAt(LocalDateTime.now().minusHours(1));
        booking2.setLatitude(10.77);
        booking2.setLongitude(106.70);
        booking2.setProblemDescription("Don 2");
        booking2.setCreatedAt(LocalDateTime.now().minusHours(2));
        booking2 = bookingRepository.save(booking2);

        // Customer 1 đánh giá 5 sao
        reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Rating 5"), customer);

        // Customer 2 đánh giá 3 sao
        reviewService.createReview(booking2.getId(), new CreateReviewRequest(3, "Rating 3"), customer2);

        ProviderProfile reloaded = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(2, reloaded.getRatingCount());
        // Trung bình (5 + 3) / 2 = 4.0
        assertEquals(4.0f, reloaded.getRatingAvg());
    }

    @Test
    @DisplayName("BE06-11: Khách lạ hoặc chính thợ đánh giá đơn -> Bị từ chối 403 Forbidden")
    void testReview_StrangerOrProvider_ThrowsAccessDenied() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now().minusHours(1));
        bookingRepository.save(booking);

        CreateReviewRequest req = new CreateReviewRequest(5, "Hack review");

        // Khách lạ
        assertThrows(AccessDeniedException.class, () ->
                reviewService.createReview(booking.getId(), req, strangerCustomer)
        );

        // Thợ tự review cho mình
        assertThrows(AccessDeniedException.class, () ->
                reviewService.createReview(booking.getId(), req, providerUser)
        );
    }

    @Test
    @DisplayName("BE06-12: Đơn chưa COMPLETED (WAITING_PAYMENT) gọi review -> Bị từ chối 409 Conflict")
    void testReview_NotCompleted_ThrowsConflict() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        CreateReviewRequest req = new CreateReviewRequest(5, "Chua xong da review");
        ConflictException ex = assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), req, customer)
        );
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    @DisplayName("BE06-13: Rating không hợp lệ (0 sao, 6 sao) -> Bị từ chối 400 Bad Request")
    void testReview_InvalidRating_ThrowsBadRequest() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now().minusHours(1));
        bookingRepository.save(booking);

        assertThrows(BadRequestException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(0, "0 sao"), customer)
        );

        assertThrows(BadRequestException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(6, "6 sao"), customer)
        );
    }

    // =========================================================================
    // 5. TRẢ DỮ LIỆU ĐÁNH GIÁ, CHI TIẾT & HỒ SƠ CÔNG KHAI
    // =========================================================================

    @Test
    @DisplayName("BE06-14: Lấy chi tiết đơn chứa đầy đủ snapshot xe, address, completedAt, reviewDeadline, isReviewed")
    void testBookingDetails_ContainsAllPaymentAndReviewFields() {
        booking.setStatus(BookingStatus.COMPLETED);
        LocalDateTime compTime = LocalDateTime.of(2026, 11, 22, 10, 0, 0);
        booking.setCompletedAt(compTime);
        booking = bookingRepository.saveAndFlush(booking);

        createQuote(booking, 1, QuoteStatus.ACCEPTED, BigDecimal.valueOf(150000));
        quoteRepository.flush();

        // Chưa review: isReviewed = false
        BookingResponse respBefore = bookingService.getBookingById(booking.getId(), customer);
        assertEquals("Honda", respBefore.vehicleBrand());
        assertEquals("Air Blade", respBefore.vehicleModel());
        assertEquals("59-A1 12345", respBefore.vehicleLicensePlate());
        assertEquals("123 Nguyen Hue, Ben Nghe, Quan 1, TP.HCM", respBefore.address());
        assertEquals(compTime, respBefore.completedAt());
        assertEquals(compTime.plusHours(168), respBefore.reviewDeadline());
        assertEquals(BigDecimal.valueOf(150000), respBefore.totalAmount());
        assertFalse(respBefore.isReviewed());

        // Đánh giá đơn
        reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Tuyet hao"), customer);

        // Sau khi review: isReviewed = true
        BookingResponse respAfter = bookingService.getBookingById(booking.getId(), customer);
        assertTrue(respAfter.isReviewed());
    }

    @Test
    @DisplayName("BE06-15: API GET /api/providers/{providerId}/reviews trả về danh sách đánh giá của thợ kèm tên khách")
    void testGetProviderReviews_Success() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now().minusHours(1));
        bookingRepository.save(booking);

        reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Tho rat nhiet tinh"), customer);

        List<ReviewResponse> reviews = reviewService.getProviderReviews(providerProfile.getId());
        assertNotNull(reviews);
        assertEquals(1, reviews.size());
        ReviewResponse rev = reviews.get(0);
        assertEquals(5, rev.rating());
        assertEquals("Tho rat nhiet tinh", rev.comment());
        assertEquals("Nguyen Van Khach", rev.customerName());
        assertEquals(providerProfile.getId(), rev.providerId());
        assertEquals(booking.getId(), rev.bookingId());
    }
}
