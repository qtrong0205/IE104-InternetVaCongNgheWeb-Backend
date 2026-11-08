package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.CancelBookingRequest;
import com.nhom6.motofix.dto.request.CreateQuoteItemRequest;
import com.nhom6.motofix.dto.request.CreateQuoteRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.QuoteResponse;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class QuoteNegotiationWorkflowTests {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private RepairQuoteRepository quoteRepository;

    @Autowired
    private BookingOfferRepository bookingOfferRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User customer;
    private User providerUser;
    private User otherUser;
    private ProviderProfile providerProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setFullName("Customer Test");
        customer.setEmail("cust_" + UUID.randomUUID() + "@test.com");
        customer.setPhone("090" + System.currentTimeMillis() % 10000000);
        customer.setPasswordHash("hash");
        customer.setStatus("active");
        customer = userRepository.save(customer);

        providerUser = new User();
        providerUser.setFullName("Provider Test");
        providerUser.setEmail("prov_" + UUID.randomUUID() + "@test.com");
        providerUser.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser.setPasswordHash("hash");
        providerUser.setStatus("active");
        providerUser = userRepository.save(providerUser);

        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Garage Test");
        providerProfile.setIdDocumentUrl("http://doc.url");
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfile = providerProfileRepository.save(providerProfile);

        otherUser = new User();
        otherUser.setFullName("Stranger Test");
        otherUser.setEmail("stranger_" + UUID.randomUUID() + "@test.com");
        otherUser.setPhone("093" + System.currentTimeMillis() % 10000000);
        otherUser.setPasswordHash("hash");
        otherUser.setStatus("active");
        otherUser = userRepository.save(otherUser);

        booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(providerProfile);
        booking.setStatus(BookingStatus.INSPECTING);
        booking.setLatitude(10.762622);
        booking.setLongitude(106.660172);
        booking.setProblemDescription("Xe bi hu dong co");
        booking.setCreatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);
    }

    private RepairQuote createTestQuote(Booking b, int version, QuoteStatus status) {
        RepairQuote quote = new RepairQuote();
        quote.setBooking(b);
        quote.setVersion(version);
        quote.setStatus(status);
        quote.setDiagnosisNote("Test quote v" + version);
        quote.setCreatedAt(LocalDateTime.now());

        RepairQuoteItem item = new RepairQuoteItem();
        item.setName("Thay bugi");
        item.setItemType(QuoteItemType.PART);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(50000));
        item.setAmount(BigDecimal.valueOf(100000));
        item.setStatus(status == QuoteStatus.ACCEPTED ? QuoteItemStatus.ACCEPTED : QuoteItemStatus.PENDING);
        item.setCreatedAt(LocalDateTime.now());
        quote.addItem(item);

        quote.setTotalAmount(BigDecimal.valueOf(100000));
        return quoteRepository.save(quote);
    }

    // ==========================================
    // 1. API ACCEPT QUOTE TESTS
    // ==========================================

    @Test
    @DisplayName("Customer chấp nhận quote v1 khi đang INSPECTING -> Quote ACCEPTED, Booking sang IN_PROGRESS")
    void testAcceptQuote_Success_TransitionsToInProgress() {
        RepairQuote quote = createTestQuote(booking, 1, QuoteStatus.PENDING);

        QuoteResponse res = quoteService.acceptQuote(booking.getId(), quote.getId(), customer);

        assertNotNull(res);
        assertEquals(QuoteStatus.ACCEPTED, res.status());
        assertNotNull(res.respondedAt());
        assertEquals(1, res.items().size());
        assertEquals(QuoteItemStatus.ACCEPTED, res.items().get(0).status());

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.IN_PROGRESS, updatedBooking.getStatus());
    }

    @Test
    @DisplayName("Người lạ hoặc Provider không được chấp nhận quote -> 403 Forbidden")
    void testAcceptQuote_Forbidden_WhenNotCustomer() {
        RepairQuote quote = createTestQuote(booking, 1, QuoteStatus.PENDING);

        assertThrows(AccessDeniedException.class, () ->
                quoteService.acceptQuote(booking.getId(), quote.getId(), providerUser)
        );
        assertThrows(AccessDeniedException.class, () ->
                quoteService.acceptQuote(booking.getId(), quote.getId(), otherUser)
        );
    }

    @Test
    @DisplayName("Không được chấp nhận quote nếu quote không ở trạng thái PENDING -> 409 Conflict")
    void testAcceptQuote_Conflict_WhenQuoteNotPending() {
        RepairQuote quote = createTestQuote(booking, 1, QuoteStatus.CANCELLED);

        assertThrows(ConflictException.class, () ->
                quoteService.acceptQuote(booking.getId(), quote.getId(), customer)
        );
    }

    @Test
    @DisplayName("Khi chấp nhận quote mới lúc đã có ACCEPTED cũ -> ACCEPTED cũ thành SUPERSEDED, quote mới thành ACCEPTED")
    void testAcceptQuote_SupersedesPreviousAcceptedQuote() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);

        RepairQuote quoteV1 = createTestQuote(booking, 1, QuoteStatus.ACCEPTED);
        RepairQuote quoteV2 = createTestQuote(booking, 2, QuoteStatus.PENDING);

        QuoteResponse res = quoteService.acceptQuote(booking.getId(), quoteV2.getId(), customer);

        assertEquals(QuoteStatus.ACCEPTED, res.status());

        RepairQuote reloadedV1 = quoteRepository.findById(quoteV1.getId()).orElseThrow();
        assertEquals(QuoteStatus.SUPERSEDED, reloadedV1.getStatus());
    }

    @Test
    @DisplayName("Chấp nhận quote khi database có nhiều hơn 1 PENDING bất thường -> 409 Conflict")
    void testAcceptQuote_Conflict_WhenAbnormalMultiplePending() {
        createTestQuote(booking, 1, QuoteStatus.PENDING);
        RepairQuote quoteV2 = createTestQuote(booking, 2, QuoteStatus.PENDING);

        assertThrows(ConflictException.class, () ->
                quoteService.acceptQuote(booking.getId(), quoteV2.getId(), customer)
        );
    }

    @Test
    @DisplayName("Chấp nhận quote khi database có nhiều hơn 1 ACCEPTED bất thường -> 409 Conflict")
    void testAcceptQuote_Conflict_WhenAbnormalMultipleAccepted() {
        createTestQuote(booking, 1, QuoteStatus.ACCEPTED);
        createTestQuote(booking, 2, QuoteStatus.ACCEPTED);
        RepairQuote quoteV3 = createTestQuote(booking, 3, QuoteStatus.PENDING);

        assertThrows(ConflictException.class, () ->
                quoteService.acceptQuote(booking.getId(), quoteV3.getId(), customer)
        );
    }

    // ==========================================
    // 2. API CANCEL BOOKING TESTS
    // ==========================================

    @Test
    @DisplayName("Customer hủy đơn thành công trước khi sửa chữa -> Booking CANCELLED, offers EXPIRED, quotes CANCELLED")
    void testCancelBooking_ByCustomer_Success() {
        RepairQuote quote = createTestQuote(booking, 1, QuoteStatus.PENDING);

        BookingOffer offer = new BookingOffer();
        offer.setBooking(booking);
        offer.setProvider(providerProfile);
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setEstimatedArrivalMinutes(15);
        offer.setExpiresAt(LocalDateTime.now().plusHours(1));
        offer.setCreatedAt(LocalDateTime.now());
        offer = bookingOfferRepository.save(offer);

        BookingResponse res = bookingService.cancelBooking(booking.getId(), new CancelBookingRequest("Khach doi y"), customer);

        assertEquals(BookingStatus.CANCELLED, res.status());

        entityManager.flush();
        entityManager.clear();

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, updatedBooking.getStatus());

        RepairQuote updatedQuote = quoteRepository.findById(quote.getId()).orElseThrow();
        assertEquals(QuoteStatus.CANCELLED, updatedQuote.getStatus());

        BookingOffer updatedOffer = bookingOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(OfferStatus.EXPIRED, updatedOffer.getStatus());
    }

    @Test
    @DisplayName("Hủy đơn có BookingMedia không bị lỗi LazyInitializationException")
    void testCancelBooking_WithMedia_Success_NoLazyInitException() {
        BookingMedia media = new BookingMedia();
        media.setBooking(booking);
        media.setUrl("https://img.url/photo.jpg");
        media.setType(MediaType.IMAGE);
        media.setCreatedAt(LocalDateTime.now());
        booking.getMedia().add(media);
        booking = bookingRepository.save(booking);

        BookingResponse res = bookingService.cancelBooking(booking.getId(), null, customer);

        assertEquals(BookingStatus.CANCELLED, res.status());
        assertNotNull(res.media());
        assertEquals(1, res.media().size());
        assertEquals("https://img.url/photo.jpg", res.media().get(0).url());
    }

    @Test
    @DisplayName("Provider được phân công có thể hủy đơn khi đang IN_PROGRESS")
    void testCancelBooking_ByProvider_Success() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);

        BookingResponse res = bookingService.cancelBooking(booking.getId(), new CancelBookingRequest("Khong co do sua"), providerUser);

        assertEquals(BookingStatus.CANCELLED, res.status());
    }

    @Test
    @DisplayName("Người lạ không thể hủy đơn -> 403 Forbidden")
    void testCancelBooking_Forbidden_Stranger() {
        assertThrows(AccessDeniedException.class, () ->
                bookingService.cancelBooking(booking.getId(), null, otherUser)
        );
    }

    @Test
    @DisplayName("Customer không thể hủy khi đơn đang IN_PROGRESS nếu không có PENDING quote -> 409 Conflict")
    void testCancelBooking_CustomerCannotCancelDuringInProgressWithoutPendingQuote() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        createTestQuote(booking, 1, QuoteStatus.ACCEPTED);

        assertThrows(ConflictException.class, () ->
                bookingService.cancelBooking(booking.getId(), null, customer)
        );
    }

    @Test
    @DisplayName("Customer CÓ THỂ hủy khi đơn đang IN_PROGRESS nếu đang có PENDING quote thương lượng")
    void testCancelBooking_CustomerCanCancelDuringInProgressWithPendingQuote() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        createTestQuote(booking, 1, QuoteStatus.ACCEPTED);
        createTestQuote(booking, 2, QuoteStatus.PENDING);

        BookingResponse res = bookingService.cancelBooking(booking.getId(), new CancelBookingRequest("Gia phat sinh qua cao"), customer);

        assertEquals(BookingStatus.CANCELLED, res.status());
    }

    @Test
    @DisplayName("Không thể hủy đơn khi đã ở trạng thái WAITING_PAYMENT hoặc CANCELLED -> 409 Conflict")
    void testCancelBooking_Conflict_WhenWaitingPaymentOrAlreadyCancelled() {
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        booking = bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                bookingService.cancelBooking(booking.getId(), null, customer)
        );

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                bookingService.cancelBooking(booking.getId(), null, customer)
        );
    }

    // ==========================================
    // 3. API REVISE QUOTE TESTS (Quy tắc mới)
    // ==========================================

    @Test
    @DisplayName("Trường hợp 1: Chỉ có PENDING, revise -> quote PENDING cũ thành CANCELLED, tạo PENDING mới, based_on_quote_id = null")
    void testReviseQuote_Case1_OnlyPending() {
        RepairQuote quoteV1 = createTestQuote(booking, 1, QuoteStatus.PENDING);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Chẩn đoán điều chỉnh lại",
                List.of(new CreateQuoteItemRequest(QuoteItemType.PART, "Linh kien moi", 1, BigDecimal.valueOf(120000), "Mo ta"))
        );

        QuoteResponse res = quoteService.reviseQuote(booking.getId(), reviseReq, providerUser);

        assertNotNull(res);
        assertEquals(2, res.version());
        assertNull(res.basedOnQuoteId()); // Chưa từng có quote ACCEPTED -> null
        assertEquals(QuoteStatus.PENDING, res.status());
        assertEquals(0, BigDecimal.valueOf(120000).compareTo(res.totalAmount()));

        // Quote v1 phải thành CANCELLED (không phải SUPERSEDED)
        RepairQuote reloadedV1 = quoteRepository.findById(quoteV1.getId()).orElseThrow();
        assertEquals(QuoteStatus.CANCELLED, reloadedV1.getStatus());
    }

    @Test
    @DisplayName("Trường hợp 2: Chỉ có ACCEPTED, revise -> giữ nguyên ACCEPTED, tạo PENDING mới trỏ vào ACCEPTED")
    void testReviseQuote_Case2_OnlyAccepted() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        RepairQuote quoteV1 = createTestQuote(booking, 1, QuoteStatus.ACCEPTED);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Phát sinh khi đang sửa",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Công phát sinh", 1, BigDecimal.valueOf(70000), "Mô tả"))
        );

        QuoteResponse res = quoteService.reviseQuote(booking.getId(), reviseReq, providerUser);

        assertNotNull(res);
        assertEquals(2, res.version());
        assertEquals(quoteV1.getId(), res.basedOnQuoteId()); // Trỏ vào quote ACCEPTED
        assertEquals(QuoteStatus.PENDING, res.status());

        // Quote v1 vẫn phải là ACCEPTED
        RepairQuote reloadedV1 = quoteRepository.findById(quoteV1.getId()).orElseThrow();
        assertEquals(QuoteStatus.ACCEPTED, reloadedV1.getStatus());
    }

    @Test
    @DisplayName("Trường hợp 3: Có cả ACCEPTED và PENDING, revise -> hủy PENDING cũ thành CANCELLED, giữ ACCEPTED, tạo PENDING mới trỏ vào ACCEPTED")
    void testReviseQuote_Case3_AcceptedAndPending() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        RepairQuote quoteV1 = createTestQuote(booking, 1, QuoteStatus.ACCEPTED);
        RepairQuote quoteV2 = createTestQuote(booking, 2, QuoteStatus.PENDING);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Chỉnh sửa lại bản phát sinh v2",
                List.of(new CreateQuoteItemRequest(QuoteItemType.PART, "Phụ tùng khác", 1, BigDecimal.valueOf(150000), "Mô tả"))
        );

        QuoteResponse res = quoteService.reviseQuote(booking.getId(), reviseReq, providerUser);

        assertNotNull(res);
        assertEquals(3, res.version());
        // based_on_quote_id TUYỆT ĐỐI trỏ vào quote v1 (ACCEPTED), KHÔNG trỏ vào v2
        assertEquals(quoteV1.getId(), res.basedOnQuoteId());
        assertEquals(QuoteStatus.PENDING, res.status());

        // Quote v1 giữ nguyên ACCEPTED
        RepairQuote reloadedV1 = quoteRepository.findById(quoteV1.getId()).orElseThrow();
        assertEquals(QuoteStatus.ACCEPTED, reloadedV1.getStatus());

        // Quote v2 chuyển thành CANCELLED
        RepairQuote reloadedV2 = quoteRepository.findById(quoteV2.getId()).orElseThrow();
        assertEquals(QuoteStatus.CANCELLED, reloadedV2.getStatus());
    }

    @Test
    @DisplayName("Revise quote khi chưa từng có ACCEPTED -> based_on_quote_id luôn bằng null")
    void testReviseQuote_NeverAccepted_BasedOnQuoteIdIsNull() {
        createTestQuote(booking, 1, QuoteStatus.PENDING);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Sửa lần 1",
                List.of(new CreateQuoteItemRequest(QuoteItemType.PART, "Item A", 1, BigDecimal.valueOf(50000), null))
        );
        QuoteResponse resV2 = quoteService.reviseQuote(booking.getId(), reviseReq, providerUser);
        assertNull(resV2.basedOnQuoteId());

        CreateQuoteRequest reviseReq2 = new CreateQuoteRequest(
                "Sửa lần 2",
                List.of(new CreateQuoteItemRequest(QuoteItemType.PART, "Item B", 1, BigDecimal.valueOf(60000), null))
        );
        QuoteResponse resV3 = quoteService.reviseQuote(booking.getId(), reviseReq2, providerUser);
        assertNull(resV3.basedOnQuoteId());
        assertEquals(3, resV3.version());
    }

    @Test
    @DisplayName("Revise khi booking chỉ có quote CANCELLED -> 409 Conflict (không có quote hợp lệ)")
    void testReviseQuote_Conflict_WhenOnlyCancelled() {
        createTestQuote(booking, 1, QuoteStatus.CANCELLED);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    @Test
    @DisplayName("Revise khi booking chỉ có quote SUPERSEDED -> 409 Conflict (không có quote hợp lệ)")
    void testReviseQuote_Conflict_WhenOnlySuperseded() {
        createTestQuote(booking, 1, QuoteStatus.SUPERSEDED);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    @Test
    @DisplayName("Revise khi booking không có bất kỳ quote nào -> 409 Conflict")
    void testReviseQuote_Conflict_WhenNoQuotes() {
        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    @Test
    @DisplayName("Revise khi database có nhiều hơn 1 quote PENDING bất thường -> 409 Conflict")
    void testReviseQuote_Conflict_WhenMultiplePending() {
        createTestQuote(booking, 1, QuoteStatus.PENDING);
        createTestQuote(booking, 2, QuoteStatus.PENDING);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    @Test
    @DisplayName("Revise khi database có nhiều hơn 1 quote ACCEPTED bất thường -> 409 Conflict")
    void testReviseQuote_Conflict_WhenMultipleAccepted() {
        createTestQuote(booking, 1, QuoteStatus.ACCEPTED);
        createTestQuote(booking, 2, QuoteStatus.ACCEPTED);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    @Test
    @DisplayName("Khách hàng hoặc Provider khác không được revise quote -> 403 Forbidden")
    void testReviseQuote_Forbidden_WhenNotAssignedProvider() {
        createTestQuote(booking, 1, QuoteStatus.PENDING);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(AccessDeniedException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, customer)
        );
        assertThrows(AccessDeniedException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, otherUser)
        );
    }

    @Test
    @DisplayName("Không thể revise quote khi Booking ở trạng thái không hợp lệ (ví dụ: CANCELLED) -> 409 Conflict")
    void testReviseQuote_Conflict_WhenInvalidBookingStatus() {
        createTestQuote(booking, 1, QuoteStatus.PENDING);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        CreateQuoteRequest reviseReq = new CreateQuoteRequest(
                "Revise attempt",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, BigDecimal.valueOf(50000), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.reviseQuote(booking.getId(), reviseReq, providerUser)
        );
    }

    // ==========================================
    // 4. API GET QUOTES LIST & DETAIL
    // ==========================================

    @Test
    @DisplayName("Customer và Provider có thể xem danh sách và chi tiết quote của booking")
    void testGetQuotes_Success() {
        RepairQuote quoteV1 = createTestQuote(booking, 1, QuoteStatus.CANCELLED);
        RepairQuote quoteV2 = createTestQuote(booking, 2, QuoteStatus.PENDING);

        List<QuoteResponse> listCust = quoteService.getQuotesForBooking(booking.getId(), customer);
        assertEquals(2, listCust.size());
        assertEquals(1, listCust.get(0).version());
        assertEquals(2, listCust.get(1).version());

        List<QuoteResponse> listProv = quoteService.getQuotesForBooking(booking.getId(), providerUser);
        assertEquals(2, listProv.size());

        QuoteResponse detail = quoteService.getQuoteDetails(booking.getId(), quoteV2.getId(), customer);
        assertEquals(quoteV2.getId(), detail.id());
        assertEquals(2, detail.version());

        // Người lạ xem quote bị 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                quoteService.getQuotesForBooking(booking.getId(), otherUser)
        );
    }
}
