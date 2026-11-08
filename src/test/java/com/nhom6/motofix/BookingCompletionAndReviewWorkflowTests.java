package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.ConfirmPaymentRequest;
import com.nhom6.motofix.dto.request.CreateReviewRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.ReviewResponse;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RepairQuoteRepository;
import com.nhom6.motofix.repository.ReviewRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BookingCompletionAndReviewWorkflowTests {

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

    private User customer;
    private User customer2;
    private User providerUser;
    private User otherProviderUser;
    private ProviderProfile providerProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        // Tạo khách hàng 1
        customer = new User();
        customer.setFullName("Customer One");
        customer.setEmail("cust1_" + UUID.randomUUID() + "@test.com");
        customer.setPhone("090" + System.currentTimeMillis() % 10000000);
        customer.setPasswordHash("hash");
        customer.setStatus("active");
        customer = userRepository.save(customer);

        // Tạo khách hàng 2
        customer2 = new User();
        customer2.setFullName("Customer Two");
        customer2.setEmail("cust2_" + UUID.randomUUID() + "@test.com");
        customer2.setPhone("092" + System.currentTimeMillis() % 10000000);
        customer2.setPasswordHash("hash");
        customer2.setStatus("active");
        customer2 = userRepository.save(customer2);

        // Tạo thợ chính
        providerUser = new User();
        providerUser.setFullName("Provider One");
        providerUser.setEmail("prov1_" + UUID.randomUUID() + "@test.com");
        providerUser.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser.setPasswordHash("hash");
        providerUser.setStatus("active");
        providerUser = userRepository.save(providerUser);

        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Garage MotoFix");
        providerProfile.setIdDocumentUrl("http://doc.url");
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfile = providerProfileRepository.save(providerProfile);

        // Tạo thợ khác
        otherProviderUser = new User();
        otherProviderUser.setFullName("Provider Two");
        otherProviderUser.setEmail("prov2_" + UUID.randomUUID() + "@test.com");
        otherProviderUser.setPhone("093" + System.currentTimeMillis() % 10000000);
        otherProviderUser.setPasswordHash("hash");
        otherProviderUser.setStatus("active");
        otherProviderUser = userRepository.save(otherProviderUser);

        // Tạo booking chuẩn
        booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(providerProfile);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setLatitude(10.762622);
        booking.setLongitude(106.660172);
        booking.setProblemDescription("Hong lop va bugi");
        booking.setCreatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);
    }

    private RepairQuote createQuote(Booking b, int version, QuoteStatus status) {
        RepairQuote quote = new RepairQuote();
        quote.setBooking(b);
        quote.setVersion(version);
        quote.setStatus(status);
        quote.setDiagnosisNote("Quote v" + version);
        quote.setTotalAmount(BigDecimal.valueOf(150000));
        quote.setCreatedAt(LocalDateTime.now());

        RepairQuoteItem item = new RepairQuoteItem();
        item.setName("Thay xam");
        item.setItemType(QuoteItemType.PART);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(150000));
        item.setAmount(BigDecimal.valueOf(150000));
        item.setStatus(status == QuoteStatus.ACCEPTED ? QuoteItemStatus.ACCEPTED : QuoteItemStatus.PENDING);
        item.setCreatedAt(LocalDateTime.now());
        quote.addItem(item);

        return quoteRepository.save(quote);
    }

    // =========================================================================
    // API 1: PATCH /api/bookings/{id}/complete
    // =========================================================================

    @Test
    @DisplayName("API 1 - Complete: Thợ hoàn thành sửa chữa thành công -> WAITING_PAYMENT")
    void testComplete_Success() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED);

        BookingResponse response = bookingService.completeBooking(booking.getId(), providerUser);

        assertNotNull(response);
        assertEquals(BookingStatus.WAITING_PAYMENT, response.status());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.WAITING_PAYMENT, reloaded.getStatus());
    }

    @Test
    @DisplayName("API 1 - Complete: Booking không tồn tại -> 404 ResourceNotFound")
    void testComplete_NotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                bookingService.completeBooking(UUID.randomUUID(), providerUser));
    }

    @Test
    @DisplayName("API 1 - Complete: Thợ lạ gọi -> 403 Forbidden")
    void testComplete_WrongProvider_Forbidden() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED);

        assertThrows(AccessDeniedException.class, () ->
                bookingService.completeBooking(booking.getId(), otherProviderUser));
    }

    @Test
    @DisplayName("API 1 - Complete: Customer gọi -> 403 Forbidden")
    void testComplete_Customer_Forbidden() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED);

        assertThrows(AccessDeniedException.class, () ->
                bookingService.completeBooking(booking.getId(), customer));
    }

    @Test
    @DisplayName("API 1 - Complete: Booking đang SEARCHING -> 409 Conflict")
    void testComplete_WrongStatus_Searching_Conflict() {
        booking.setStatus(BookingStatus.SEARCHING);
        bookingRepository.save(booking);
        createQuote(booking, 1, QuoteStatus.ACCEPTED);

        assertThrows(ConflictException.class, () ->
                bookingService.completeBooking(booking.getId(), providerUser));
    }

    @Test
    @DisplayName("API 1 - Complete: Booking đang WAITING_PAYMENT -> 409 Conflict")
    void testComplete_WrongStatus_WaitingPayment_Conflict() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);
        createQuote(booking, 1, QuoteStatus.ACCEPTED);

        assertThrows(ConflictException.class, () ->
                bookingService.completeBooking(booking.getId(), providerUser));
    }

    @Test
    @DisplayName("API 1 - Complete: Chưa có quote ACCEPTED -> 409 Conflict")
    void testComplete_NoAcceptedQuote_Conflict() {
        // Chỉ có quote PENDING
        createQuote(booking, 1, QuoteStatus.PENDING);

        assertThrows(ConflictException.class, () ->
                bookingService.completeBooking(booking.getId(), providerUser));
    }

    @Test
    @DisplayName("API 1 - Complete: Còn quote PENDING đang chờ duyệt -> 409 Conflict")
    void testComplete_HasPendingQuote_Conflict() {
        createQuote(booking, 1, QuoteStatus.ACCEPTED);
        createQuote(booking, 2, QuoteStatus.PENDING);

        assertThrows(ConflictException.class, () ->
                bookingService.completeBooking(booking.getId(), providerUser));
    }

    // =========================================================================
    // API 2: PATCH /api/bookings/{id}/payment/confirm
    // =========================================================================

    @Test
    @DisplayName("API 2 - Payment Confirm: Tiền mặt (CASH) thành công -> COMPLETED")
    void testConfirmPayment_Cash_Success() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        BookingResponse response = bookingService.confirmPayment(
                booking.getId(),
                new ConfirmPaymentRequest(PaymentMethod.CASH),
                providerUser
        );

        assertNotNull(response);
        assertEquals(BookingStatus.COMPLETED, response.status());
        assertEquals(PaymentMethod.CASH, response.paymentMethod());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.COMPLETED, reloaded.getStatus());
        assertEquals(PaymentMethod.CASH, reloaded.getPaymentMethod());
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Chuyển khoản (BANK_TRANSFER) thành công -> COMPLETED")
    void testConfirmPayment_BankTransfer_Success() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        BookingResponse response = bookingService.confirmPayment(
                booking.getId(),
                new ConfirmPaymentRequest(PaymentMethod.BANK_TRANSFER),
                providerUser
        );

        assertNotNull(response);
        assertEquals(BookingStatus.COMPLETED, response.status());
        assertEquals(PaymentMethod.BANK_TRANSFER, response.paymentMethod());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.COMPLETED, reloaded.getStatus());
        assertEquals(PaymentMethod.BANK_TRANSFER, reloaded.getPaymentMethod());
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Booking không tồn tại -> 404 ResourceNotFound")
    void testConfirmPayment_NotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                bookingService.confirmPayment(
                        UUID.randomUUID(),
                        new ConfirmPaymentRequest(PaymentMethod.CASH),
                        providerUser
                ));
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Thợ lạ gọi -> 403 Forbidden")
    void testConfirmPayment_WrongProvider_Forbidden() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        assertThrows(AccessDeniedException.class, () ->
                bookingService.confirmPayment(
                        booking.getId(),
                        new ConfirmPaymentRequest(PaymentMethod.CASH),
                        otherProviderUser
                ));
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Customer gọi -> 403 Forbidden")
    void testConfirmPayment_Customer_Forbidden() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        assertThrows(AccessDeniedException.class, () ->
                bookingService.confirmPayment(
                        booking.getId(),
                        new ConfirmPaymentRequest(PaymentMethod.CASH),
                        customer
                ));
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Booking chưa ở WAITING_PAYMENT (IN_PROGRESS) -> 409 Conflict")
    void testConfirmPayment_WrongStatus_InProgress_Conflict() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                bookingService.confirmPayment(
                        booking.getId(),
                        new ConfirmPaymentRequest(PaymentMethod.CASH),
                        providerUser
                ));
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Xác nhận lần thứ hai (đã COMPLETED) -> 409 Conflict")
    void testConfirmPayment_AlreadyCompleted_SecondCall_Conflict() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        // Xác nhận lần 1
        bookingService.confirmPayment(booking.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH), providerUser);

        // Xác nhận lần 2 -> Phải từ chối vì không còn là WAITING_PAYMENT
        assertThrows(ConflictException.class, () ->
                bookingService.confirmPayment(
                        booking.getId(),
                        new ConfirmPaymentRequest(PaymentMethod.CASH),
                        providerUser
                ));
    }

    @Test
    @DisplayName("API 2 - Payment Confirm: Thiếu payment method -> 409 Conflict")
    void testConfirmPayment_NullMethod_Conflict() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                bookingService.confirmPayment(booking.getId(), new ConfirmPaymentRequest(null), providerUser));
    }

    // =========================================================================
    // API 3: POST /api/bookings/{id}/review
    // =========================================================================

    @Test
    @DisplayName("API 3 - Review: Khách hàng đánh giá thành công (rating 5) -> tạo Review, cập nhật ProviderProfile")
    void testCreateReview_Success() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        CreateReviewRequest request = new CreateReviewRequest(5, "Dich vu rat nhanh va chuyen nghiep!");
        ReviewResponse response = reviewService.createReview(booking.getId(), request, customer);

        assertNotNull(response);
        assertEquals(booking.getId(), response.bookingId());
        assertEquals(providerProfile.getId(), response.providerId());
        assertEquals(5, response.rating());
        assertEquals("Dich vu rat nhanh va chuyen nghiep!", response.comment());
        assertNotNull(response.createdAt());

        // Kiểm tra ProviderProfile đã được cập nhật
        ProviderProfile reloadedProvider = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(1, reloadedProvider.getRatingCount());
        assertEquals(5.0f, reloadedProvider.getRatingAvg());
    }

    @Test
    @DisplayName("API 3 - Review: Rating biên độ 1 sao thành công")
    void testCreateReview_RatingBoundary_1() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        CreateReviewRequest request = new CreateReviewRequest(1, "Khong hai long");
        ReviewResponse response = reviewService.createReview(booking.getId(), request, customer);

        assertEquals(1, response.rating());
        ProviderProfile reloadedProvider = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(1, reloadedProvider.getRatingCount());
        assertEquals(1.0f, reloadedProvider.getRatingAvg());
    }

    @Test
    @DisplayName("API 3 - Review: Comment null vẫn hợp lệ")
    void testCreateReview_NullComment_Success() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        CreateReviewRequest request = new CreateReviewRequest(4, null);
        ReviewResponse response = reviewService.createReview(booking.getId(), request, customer);

        assertNotNull(response);
        assertEquals(4, response.rating());
        assertNull(response.comment());
    }

    @Test
    @DisplayName("API 3 - Review: Booking không tồn tại -> 404 ResourceNotFound")
    void testCreateReview_NotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                reviewService.createReview(UUID.randomUUID(), new CreateReviewRequest(5, "Good"), customer));
    }

    @Test
    @DisplayName("API 3 - Review: Thợ tự đánh giá đơn của mình -> 403 Forbidden")
    void testCreateReview_ProviderSelfReview_Forbidden() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        assertThrows(AccessDeniedException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Tu khen minh"), providerUser));
    }

    @Test
    @DisplayName("API 3 - Review: Khách hàng lạ đánh giá đơn của người khác -> 403 Forbidden")
    void testCreateReview_WrongCustomer_Forbidden() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        assertThrows(AccessDeniedException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Khach la"), customer2));
    }

    @Test
    @DisplayName("API 3 - Review: Booking chưa COMPLETED (đang WAITING_PAYMENT) -> 409 Conflict")
    void testCreateReview_WrongStatus_WaitingPayment_Conflict() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Chua xong"), customer));
    }

    @Test
    @DisplayName("API 3 - Review: Đánh giá lần thứ 2 cho cùng 1 booking -> 409 Conflict")
    void testCreateReview_DuplicateReview_Conflict() {
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        // Đánh giá lần 1
        reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Tot"), customer);

        // Đánh giá lần 2
        assertThrows(ConflictException.class, () ->
                reviewService.createReview(booking.getId(), new CreateReviewRequest(4, "Sua lai"), customer));
    }

    @Test
    @DisplayName("API 3 - Review: Nhiều lượt đánh giá cho thợ -> tính chính xác rating_count và rating_avg")
    void testCreateReview_MultipleReviews_CalculatesCorrectAverage() {
        // Đơn 1: Khách 1 đánh giá 5 sao
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        reviewService.createReview(booking.getId(), new CreateReviewRequest(5, "Qua tot"), customer);

        // Tạo đơn 2 cho cùng thợ, hoàn thành và Khách 2 đánh giá 4 sao
        Booking booking2 = new Booking();
        booking2.setCustomer(customer2);
        booking2.setProvider(providerProfile);
        booking2.setStatus(BookingStatus.COMPLETED);
        booking2.setLatitude(10.762622);
        booking2.setLongitude(106.660172);
        booking2.setProblemDescription("Thu xe");
        booking2.setCreatedAt(LocalDateTime.now());
        booking2 = bookingRepository.save(booking2);

        reviewService.createReview(booking2.getId(), new CreateReviewRequest(4, "Kha tot"), customer2);

        // Kiểm tra điểm trung bình: (5 + 4) / 2 = 4.5, count = 2
        ProviderProfile reloadedProvider = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(2, reloadedProvider.getRatingCount());
        assertEquals(4.5f, reloadedProvider.getRatingAvg());
    }
}

