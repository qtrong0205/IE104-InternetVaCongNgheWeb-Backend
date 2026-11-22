package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.CancelBookingRequest;
import com.nhom6.motofix.dto.request.ConfirmPaymentRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class BE05StateMachineAndQuoteVersionTests {

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

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(LocalDateTime.class, new com.fasterxml.jackson.databind.JsonSerializer<LocalDateTime>() {
                        @Override
                        public void serialize(LocalDateTime value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                            gen.writeString(value != null ? value.toString() : null);
                        }
                    }));

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User customer;
    private User providerUser;
    private User otherCustomer;
    private User otherProviderUser;
    private ProviderProfile providerProfile;
    private ProviderProfile otherProviderProfile;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setFullName("Customer BE05");
        customer.setEmail("cust_be05_" + UUID.randomUUID() + "@test.com");
        customer.setPhone("090" + System.currentTimeMillis() % 10000000);
        customer.setPasswordHash("hash");
        customer.setStatus("active");
        customer = userRepository.save(customer);

        otherCustomer = new User();
        otherCustomer.setFullName("Other Customer BE05");
        otherCustomer.setEmail("other_cust_be05_" + UUID.randomUUID() + "@test.com");
        otherCustomer.setPhone("091" + System.currentTimeMillis() % 10000000);
        otherCustomer.setPasswordHash("hash");
        otherCustomer.setStatus("active");
        otherCustomer = userRepository.save(otherCustomer);

        providerUser = new User();
        providerUser.setFullName("Provider BE05");
        providerUser.setEmail("prov_be05_" + UUID.randomUUID() + "@test.com");
        providerUser.setPhone("092" + System.currentTimeMillis() % 10000000);
        providerUser.setPasswordHash("hash");
        providerUser.setStatus("active");
        providerUser = userRepository.save(providerUser);

        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Garage BE05");
        providerProfile.setIdDocumentUrl("http://doc.url");
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfile = providerProfileRepository.save(providerProfile);

        otherProviderUser = new User();
        otherProviderUser.setFullName("Other Provider BE05");
        otherProviderUser.setEmail("other_prov_be05_" + UUID.randomUUID() + "@test.com");
        otherProviderUser.setPhone("093" + System.currentTimeMillis() % 10000000);
        otherProviderUser.setPasswordHash("hash");
        otherProviderUser.setStatus("active");
        otherProviderUser = userRepository.save(otherProviderUser);

        otherProviderProfile = new ProviderProfile();
        otherProviderProfile.setUser(otherProviderUser);
        otherProviderProfile.setBusinessName("Other Garage BE05");
        otherProviderProfile.setIdDocumentUrl("http://other.url");
        otherProviderProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        otherProviderProfile.setActive(true);
        otherProviderProfile = providerProfileRepository.save(otherProviderProfile);
    }

    private Booking createBookingInStatus(BookingStatus status) {
        Booking b = new Booking();
        b.setCustomer(customer);
        if (status != BookingStatus.SEARCHING) {
            b.setProvider(providerProfile);
        }
        b.setStatus(status);
        b.setAddress("123 Hai Ba Trung, Q1, HCMC");
        b.setLatitude(10.7769);
        b.setLongitude(106.7009);
        return bookingRepository.save(b);
    }

    private RepairQuote createQuote(Booking b, int version, RepairQuote basedOn, QuoteStatus status, BigDecimal amount) {
        RepairQuote quote = new RepairQuote();
        quote.setBooking(b);
        quote.setVersion(version);
        quote.setBasedOnQuote(basedOn);
        quote.setDiagnosisNote("Diagnosis v" + version);
        quote.setStatus(status);
        quote.setTotalAmount(amount);
        quote.setCreatedAt(LocalDateTime.now());
        if (status == QuoteStatus.ACCEPTED) {
            quote.setRespondedAt(LocalDateTime.now());
        }

        RepairQuoteItem item = new RepairQuoteItem();
        item.setItemType(QuoteItemType.LABOR);
        item.setName("Item v" + version);
        item.setQuantity(1);
        item.setUnitPrice(amount);
        item.setAmount(amount);
        item.setCreatedAt(LocalDateTime.now());
        quote.addItem(item);

        return quoteRepository.save(quote);
    }

    @Test
    @Transactional
    @DisplayName("1. Máy trạng thái: Luồng chuẩn tuần tự từ SELECTED_PROVIDER đến COMPLETED")
    void testSequentialStateMachineTransitions() {
        Booking b = createBookingInStatus(BookingStatus.SELECTED_PROVIDER);

        // 1. SELECTED_PROVIDER -> PROVIDER_EN_ROUTE
        BookingResponse res1 = bookingService.confirmEnRoute(b.getId(), providerUser);
        assertEquals(BookingStatus.PROVIDER_EN_ROUTE, res1.status());

        // 2. PROVIDER_EN_ROUTE -> INSPECTING
        BookingResponse res2 = bookingService.confirmArrived(b.getId(), providerUser);
        assertEquals(BookingStatus.INSPECTING, res2.status());

        // 3. Thợ tạo báo giá v1 PENDING khi đang INSPECTING
        CreateQuoteItemRequest item1 = new CreateQuoteItemRequest(
                QuoteItemType.LABOR, "Tien cong sua chua", 1, new BigDecimal("150000"), "Cong kham va sua"
        );
        CreateQuoteItemRequest item2 = new CreateQuoteItemRequest(
                QuoteItemType.PART, "Bugi NGK", 2, new BigDecimal("80000"), "Thay 2 bugi"
        );
        QuoteResponse quoteV1 = quoteService.createInitialQuote(b.getId(), new CreateQuoteRequest("Kiem tra xe", List.of(item1, item2)), providerUser);
        assertEquals(1, quoteV1.version());
        assertEquals(QuoteStatus.PENDING, quoteV1.status());
        assertNull(quoteV1.basedOnQuoteId());
        // Backend tự tính: 150000*1 + 80000*2 = 310000
        assertEquals(0, new BigDecimal("310000").compareTo(quoteV1.totalAmount()));

        // 4. Khách chấp nhận v1 -> INSPECTING chuyển sang IN_PROGRESS
        QuoteResponse acceptedV1 = quoteService.acceptQuote(b.getId(), quoteV1.id(), customer);
        assertEquals(QuoteStatus.ACCEPTED, acceptedV1.status());
        Booking reloadedBooking = bookingRepository.findById(b.getId()).orElseThrow();
        assertEquals(BookingStatus.IN_PROGRESS, reloadedBooking.getStatus());

        // 5. Thợ hoàn thành sửa chữa (không cần payload checklist) -> WAITING_PAYMENT
        BookingResponse resComplete = bookingService.completeBooking(b.getId(), providerUser);
        assertEquals(BookingStatus.WAITING_PAYMENT, resComplete.status());

        // 6. Thợ xác nhận thanh toán tiền mặt (CASH) -> COMPLETED
        BookingResponse resPayment = bookingService.confirmPayment(b.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH), providerUser);
        assertEquals(BookingStatus.COMPLETED, resPayment.status());
        assertEquals(PaymentMethod.CASH, resPayment.paymentMethod());
    }

    @Test
    @Transactional
    @DisplayName("2. Ma trận hủy: SEARCHING chỉ khách hủy được, thợ không thể hủy")
    void testCancelSearchingOnlyCustomer() {
        Booking b = createBookingInStatus(BookingStatus.SEARCHING);

        // Provider lạ cố hủy -> 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                bookingService.cancelBooking(b.getId(), new CancelBookingRequest("Tho muon huy"), providerUser));

        // Khách hủy -> thành công
        BookingResponse res = bookingService.cancelBooking(b.getId(), new CancelBookingRequest("Doi y"), customer);
        assertEquals(BookingStatus.CANCELLED, res.status());
    }

    @Test
    @Transactional
    @DisplayName("3. Ma trận hủy: Cả khách và thợ đều hủy được ở SELECTED_PROVIDER, PROVIDER_EN_ROUTE, INSPECTING")
    void testCancelInEarlyStatuses() {
        // SELECTED_PROVIDER: Thợ hủy thành công
        Booking b1 = createBookingInStatus(BookingStatus.SELECTED_PROVIDER);
        BookingResponse res1 = bookingService.cancelBooking(b1.getId(), new CancelBookingRequest("Xe hong"), providerUser);
        assertEquals(BookingStatus.CANCELLED, res1.status());

        // PROVIDER_EN_ROUTE: Khách hủy thành công
        Booking b2 = createBookingInStatus(BookingStatus.PROVIDER_EN_ROUTE);
        BookingResponse res2 = bookingService.cancelBooking(b2.getId(), new CancelBookingRequest("Khong can nua"), customer);
        assertEquals(BookingStatus.CANCELLED, res2.status());

        // INSPECTING: Khách hủy thành công
        Booking b3 = createBookingInStatus(BookingStatus.INSPECTING);
        BookingResponse res3 = bookingService.cancelBooking(b3.getId(), new CancelBookingRequest("Gia qua cao"), customer);
        assertEquals(BookingStatus.CANCELLED, res3.status());
    }

    @Test
    @Transactional
    @DisplayName("4. Ma trận hủy: IN_PROGRESS - Thợ luôn được hủy; Khách CHỈ được hủy khi có quote PENDING")
    void testCancelInProgressRules() {
        // Trường hợp 4a: IN_PROGRESS và KHÔNG CÓ quote PENDING (chỉ có quote ACCEPTED)
        Booking b = createBookingInStatus(BookingStatus.IN_PROGRESS);
        createQuote(b, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));

        // Khách cố hủy -> BỊ CHẶN 409 Conflict
        ConflictException exCust = assertThrows(ConflictException.class, () ->
                bookingService.cancelBooking(b.getId(), new CancelBookingRequest("Khach muon huy"), customer));
        assertTrue(exCust.getMessage().contains("pending quote"));

        // Thợ hủy -> THÀNH CÔNG
        BookingResponse resProv = bookingService.cancelBooking(b.getId(), new CancelBookingRequest("Tho gap su co"), providerUser);
        assertEquals(BookingStatus.CANCELLED, resProv.status());

        // Trường hợp 4b: IN_PROGRESS và CÓ quote PENDING phát sinh
        Booking b2 = createBookingInStatus(BookingStatus.IN_PROGRESS);
        RepairQuote acceptedQuote = createQuote(b2, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));
        RepairQuote pendingQuote = createQuote(b2, 2, acceptedQuote, QuoteStatus.PENDING, new BigDecimal("500000"));

        // Thêm các offer
        BookingOffer offerPending = new BookingOffer();
        offerPending.setBooking(b2);
        offerPending.setProvider(providerProfile);
        offerPending.setStatus(OfferStatus.PENDING);
        offerPending.setDistanceM(1000.0);
        offerPending.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        bookingOfferRepository.save(offerPending);

        BookingOffer offerAccepted = new BookingOffer();
        offerAccepted.setBooking(b2);
        offerAccepted.setProvider(providerProfile);
        offerAccepted.setStatus(OfferStatus.ACCEPTED);
        offerAccepted.setDistanceM(1000.0);
        offerAccepted.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        bookingOfferRepository.save(offerAccepted);

        // Khách hủy khi có quote PENDING -> THÀNH CÔNG
        BookingResponse resCust = bookingService.cancelBooking(b2.getId(), new CancelBookingRequest("Gia phat sinh qua dat"), customer);
        assertEquals(BookingStatus.CANCELLED, resCust.status());

        // Dọn dẹp trong cùng giao dịch:
        // - Toàn bộ offer PENDING và ACCEPTED chuyển sang EXPIRED
        BookingOffer reloadedOfferP = bookingOfferRepository.findById(offerPending.getId()).orElseThrow();
        BookingOffer reloadedOfferA = bookingOfferRepository.findById(offerAccepted.getId()).orElseThrow();
        assertEquals(OfferStatus.EXPIRED, reloadedOfferP.getStatus());
        assertEquals(OfferStatus.EXPIRED, reloadedOfferA.getStatus());

        // - Quote PENDING chuyển sang CANCELLED
        RepairQuote reloadedPendingQ = quoteRepository.findById(pendingQuote.getId()).orElseThrow();
        assertEquals(QuoteStatus.CANCELLED, reloadedPendingQ.getStatus());

        // - Giữ chứng từ quote ACCEPTED cũ (vẫn là ACCEPTED) để làm lịch sử
        RepairQuote reloadedAcceptedQ = quoteRepository.findById(acceptedQuote.getId()).orElseThrow();
        assertEquals(QuoteStatus.ACCEPTED, reloadedAcceptedQ.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("5. Ma trận hủy: Không ai được hủy khi WAITING_PAYMENT hoặc COMPLETED hoặc CANCELLED")
    void testCannotCancelInTerminalStatuses() {
        Booking bWaiting = createBookingInStatus(BookingStatus.WAITING_PAYMENT);
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bWaiting.getId(), null, customer));
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bWaiting.getId(), null, providerUser));

        Booking bCompleted = createBookingInStatus(BookingStatus.COMPLETED);
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bCompleted.getId(), null, customer));
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bCompleted.getId(), null, providerUser));

        Booking bCancelled = createBookingInStatus(BookingStatus.CANCELLED);
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bCancelled.getId(), null, customer));
        assertThrows(ConflictException.class, () -> bookingService.cancelBooking(bCancelled.getId(), null, providerUser));
    }

    @Test
    @Transactional
    @DisplayName("6. Stale Accept: Khách accept quote cũ đã bị revise/rollback chuyển thành CANCELLED -> 409 Conflict")
    void testStaleAcceptAfterReviseOrRollback() {
        Booking b = createBookingInStatus(BookingStatus.INSPECTING);

        // v1 PENDING
        CreateQuoteItemRequest item1 = new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, new BigDecimal("100000"), "Mo ta");
        QuoteResponse v1 = quoteService.createInitialQuote(b.getId(), new CreateQuoteRequest("Chan doan v1", List.of(item1)), providerUser);
        assertEquals(1, v1.version());
        assertEquals(QuoteStatus.PENDING, v1.status());

        // Thợ sửa báo giá (revise) trước khi khách kịp bấm accept -> v1 bị CANCELLED, v2 PENDING tạo mới
        CreateQuoteItemRequest item2 = new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong sua lai", 1, new BigDecimal("120000"), "Mo ta moi");
        QuoteResponse v2 = quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Chan doan v2", List.of(item2)), providerUser);
        assertEquals(2, v2.version());
        assertEquals(QuoteStatus.PENDING, v2.status());

        // Khách từ giao diện cũ bấm accept ID của v1 -> BỊ TỪ CHỐI 409 Conflict "Only PENDING quotes can be accepted", KHÔNG tự nhảy sang v2
        ConflictException exStale = assertThrows(ConflictException.class, () ->
                quoteService.acceptQuote(b.getId(), v1.id(), customer));
        assertTrue(exStale.getMessage().contains("Only PENDING quotes can be accepted"));

        // Kiểm tra database: v1 vẫn CANCELLED, v2 vẫn PENDING, booking vẫn INSPECTING
        RepairQuote reloadedV1 = quoteRepository.findById(v1.id()).orElseThrow();
        RepairQuote reloadedV2 = quoteRepository.findById(v2.id()).orElseThrow();
        assertEquals(QuoteStatus.CANCELLED, reloadedV1.getStatus());
        assertEquals(QuoteStatus.PENDING, reloadedV2.getStatus());
        assertEquals(BookingStatus.INSPECTING, bookingRepository.findById(b.getId()).orElseThrow().getStatus());

        // Khi khách bấm accept đúng v2 -> THÀNH CÔNG
        QuoteResponse acceptedV2 = quoteService.acceptQuote(b.getId(), v2.id(), customer);
        assertEquals(QuoteStatus.ACCEPTED, acceptedV2.status());
        assertEquals(BookingStatus.IN_PROGRESS, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    @DisplayName("7. Stale Accept: Khách accept quote v2 PENDING sau khi thợ đã Rollback về v1 -> 409 Conflict")
    void testStaleAcceptAfterRollback() {
        Booking b = createBookingInStatus(BookingStatus.IN_PROGRESS);
        RepairQuote v1Accepted = createQuote(b, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));
        RepairQuote v2Pending = createQuote(b, 2, v1Accepted, QuoteStatus.PENDING, new BigDecimal("350000"));

        // Thợ rollback v2 về lại v1 -> v2 thành CANCELLED
        QuoteResponse rollbackRes = quoteService.rollbackQuote(b.getId(), providerUser);
        assertEquals(v1Accepted.getId(), rollbackRes.id());
        assertEquals(QuoteStatus.ACCEPTED, rollbackRes.status());

        // Khách bấm accept v2 (đã bị rollback) -> 409 Conflict
        ConflictException ex = assertThrows(ConflictException.class, () ->
                quoteService.acceptQuote(b.getId(), v2Pending.getId(), customer));
        assertTrue(ex.getMessage().contains("Only PENDING quotes can be accepted"));
    }

    @Test
    @DisplayName("8. Concurrency: Khách accept đồng thời với thợ revise quote")
    void testConcurrentAcceptAndRevise() throws Exception {
        // Tạo booking và quote ngoài transaction của test để 2 luồng riêng biệt truy cập qua DB
        Booking b = createBookingInStatus(BookingStatus.INSPECTING);
        RepairQuote v1 = createQuote(b, 1, null, QuoteStatus.PENDING, new BigDecimal("200000"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Boolean> customerAccept = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                quoteService.acceptQuote(b.getId(), v1.getId(), customer);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Callable<Boolean> providerRevise = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                CreateQuoteItemRequest item = new CreateQuoteItemRequest(
                        QuoteItemType.LABOR, "Cong moi", 1, new BigDecimal("250000"), "Mo ta moi"
                );
                quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Revise concurrency", List.of(item)), providerUser);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> futAccept = executor.submit(customerAccept);
        Future<Boolean> futRevise = executor.submit(providerRevise);

        boolean acceptOk = futAccept.get(10, TimeUnit.SECONDS);
        boolean reviseOk = futRevise.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        // Do có Pessimistic Lock trên Booking:
        // Cả 2 luồng đều hợp lệ tuần tự hóa:
        // - Hoặc accept chạy trước: v1 thành ACCEPTED. Luồng revise chạy sau -> revise TH2 (tạo v2 PENDING dựa trên v1 ACCEPTED) -> cả 2 đều true.
        // - Hoặc revise chạy trước: v1 thành CANCELLED, v2 PENDING. Luồng accept v1 chạy sau -> stale accept ném 409 Conflict (acceptOk = false, reviseOk = true).
        // Trong mọi kịch bản: Số lượng quote PENDING tối đa là 1, số lượng ACCEPTED tối đa là 1. Không bao giờ xảy ra tình trạng 2 PENDING.
        List<RepairQuote> pendings = quoteRepository.findByBookingIdAndStatus(b.getId(), QuoteStatus.PENDING);
        List<RepairQuote> accepteds = quoteRepository.findByBookingIdAndStatus(b.getId(), QuoteStatus.ACCEPTED);
        assertTrue(pendings.size() <= 1, "Số lượng quote PENDING tối đa là 1");
        assertTrue(accepteds.size() <= 1, "Số lượng quote ACCEPTED tối đa là 1");
    }

    @Test
    @Transactional
    @DisplayName("9. Complete Repair: Chặn khi còn quote PENDING đang chờ duyệt -> 409 Conflict")
    void testCompleteBlockedWhenPendingQuoteExists() {
        Booking b = createBookingInStatus(BookingStatus.IN_PROGRESS);
        RepairQuote v1 = createQuote(b, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));
        RepairQuote v2 = createQuote(b, 2, v1, QuoteStatus.PENDING, new BigDecimal("300000"));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                bookingService.completeBooking(b.getId(), providerUser));
        assertTrue(ex.getMessage().contains("PENDING quote awaiting approval"));
    }

    @Test
    @Transactional
    @DisplayName("10. Complete Repair: Thành công khi đúng 1 ACCEPTED và 0 PENDING, không cần payload checklist")
    void testCompleteSuccessWithoutChecklistPayload() {
        Booking b = createBookingInStatus(BookingStatus.IN_PROGRESS);
        createQuote(b, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));

        BookingResponse res = bookingService.completeBooking(b.getId(), providerUser);
        assertEquals(BookingStatus.WAITING_PAYMENT, res.status());
    }

    @Test
    @Transactional
    @DisplayName("11. Complete Repair: Chặn người lạ hoặc thợ khác hoàn thành sửa xe -> 403 Forbidden")
    void testCompleteUnauthorized() {
        Booking b = createBookingInStatus(BookingStatus.IN_PROGRESS);
        createQuote(b, 1, null, QuoteStatus.ACCEPTED, new BigDecimal("200000"));

        assertThrows(AccessDeniedException.class, () ->
                bookingService.completeBooking(b.getId(), customer));
        assertThrows(AccessDeniedException.class, () ->
                bookingService.completeBooking(b.getId(), otherProviderUser));
    }

    @Test
    @Transactional
    @DisplayName("12. Kiểm chứng tính tiền tự động và lịch sử version qua 3 trường hợp revise")
    void testAutomaticPricingAndVersioningChain() {
        Booking b = createBookingInStatus(BookingStatus.INSPECTING);

        // --- BƯỚC 1: Tạo v1 ban đầu (PENDING, based_on = null) ---
        CreateQuoteItemRequest item1 = new CreateQuoteItemRequest(QuoteItemType.LABOR, "Công khám", 2, new BigDecimal("75000"), null);
        QuoteResponse v1 = quoteService.createInitialQuote(b.getId(), new CreateQuoteRequest("Khám", List.of(item1)), providerUser);
        assertEquals(1, v1.version());
        assertNull(v1.basedOnQuoteId());
        assertEquals(0, new BigDecimal("150000").compareTo(v1.totalAmount()));

        // --- BƯỚC 2: Revise TH1 (Chỉ có PENDING) -> v1 CANCELLED, v2 PENDING, based_on = null ---
        CreateQuoteItemRequest item2 = new CreateQuoteItemRequest(QuoteItemType.PART, "Lọc gió", 1, new BigDecimal("120000"), null);
        QuoteResponse v2 = quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Sửa lần 1", List.of(item2)), providerUser);
        assertEquals(2, v2.version());
        assertNull(v2.basedOnQuoteId(), "Revise TH1 khi chưa có ACCEPTED thì based_on_quote_id phải là null");
        assertEquals(QuoteStatus.PENDING, v2.status());
        assertEquals(QuoteStatus.CANCELLED, quoteRepository.findById(v1.id()).orElseThrow().getStatus());

        // Khách chấp nhận v2 -> v2 thành ACCEPTED, Booking chuyển sang IN_PROGRESS
        QuoteResponse v2Accepted = quoteService.acceptQuote(b.getId(), v2.id(), customer);
        assertEquals(QuoteStatus.ACCEPTED, v2Accepted.status());
        assertEquals(BookingStatus.IN_PROGRESS, bookingRepository.findById(b.getId()).orElseThrow().getStatus());

        // --- BƯỚC 3: Revise TH2 (Chỉ có ACCEPTED) -> v2 ACCEPTED giữ nguyên, v3 PENDING trỏ vào v2 ---
        CreateQuoteItemRequest item3a = new CreateQuoteItemRequest(QuoteItemType.PART, "Lọc gió", 1, new BigDecimal("120000"), null);
        CreateQuoteItemRequest item3b = new CreateQuoteItemRequest(QuoteItemType.PART, "Bugi", 1, new BigDecimal("90000"), null);
        QuoteResponse v3 = quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Phát sinh thêm bugi", List.of(item3a, item3b)), providerUser);
        assertEquals(3, v3.version());
        assertEquals(v2.id(), v3.basedOnQuoteId(), "Revise TH2 phải trỏ vào v2 ACCEPTED");
        assertEquals(QuoteStatus.PENDING, v3.status());
        assertEquals(0, new BigDecimal("210000").compareTo(v3.totalAmount()));
        assertEquals(QuoteStatus.ACCEPTED, quoteRepository.findById(v2.id()).orElseThrow().getStatus());

        // --- BƯỚC 4: Revise TH3 (Có cả ACCEPTED và PENDING) -> v3 CANCELLED, v4 PENDING vẫn trỏ vào v2 ACCEPTED ---
        CreateQuoteItemRequest item4 = new CreateQuoteItemRequest(QuoteItemType.PART, "Dầu nhớt", 1, new BigDecimal("160000"), null);
        QuoteResponse v4 = quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Đổi phương án", List.of(item4)), providerUser);
        assertEquals(4, v4.version());
        assertEquals(v2.id(), v4.basedOnQuoteId(), "Revise TH3 tuyệt đối không trỏ vào v3 PENDING mà phải trỏ vào v2 ACCEPTED");
        assertEquals(QuoteStatus.PENDING, v4.status());
        assertEquals(QuoteStatus.CANCELLED, quoteRepository.findById(v3.id()).orElseThrow().getStatus());
        assertEquals(QuoteStatus.ACCEPTED, quoteRepository.findById(v2.id()).orElseThrow().getStatus());

        // Khách chấp nhận v4 -> v2 ACCEPTED cũ thành SUPERSEDED, v4 thành ACCEPTED
        QuoteResponse v4Accepted = quoteService.acceptQuote(b.getId(), v4.id(), customer);
        assertEquals(QuoteStatus.ACCEPTED, v4Accepted.status());
        assertEquals(QuoteStatus.SUPERSEDED, quoteRepository.findById(v2.id()).orElseThrow().getStatus());

        // --- BƯỚC 5: Xem toàn bộ lịch sử (getQuotesForBooking) ---
        List<QuoteResponse> history = quoteService.getQuotesForBooking(b.getId(), customer);
        assertEquals(4, history.size());
        assertEquals(1, history.get(0).version());
        assertEquals(QuoteStatus.CANCELLED, history.get(0).status());
        assertEquals(2, history.get(1).version());
        assertEquals(QuoteStatus.SUPERSEDED, history.get(1).status());
        assertEquals(3, history.get(2).version());
        assertEquals(QuoteStatus.CANCELLED, history.get(2).status());
        assertEquals(4, history.get(3).version());
        assertEquals(QuoteStatus.ACCEPTED, history.get(3).status());
        assertEquals(v2.id(), history.get(3).basedOnQuoteId());
    }

    @Test
    @Transactional
    @DisplayName("13. Kiểm tra JSON thực tế của API: Quote cha có status, mọi item KHÔNG có status hay effectiveStatus (Bao phủ SUPERSEDED và ACCEPTED)")
    void testApiJsonRepresentationWithoutItemStatus() throws Exception {
        Booking b = createBookingInStatus(BookingStatus.INSPECTING);

        // 1. Thợ tạo v1 (PENDING)
        CreateQuoteItemRequest item1 = new CreateQuoteItemRequest(QuoteItemType.LABOR, "Công khám", 1, new BigDecimal("100000"), "Mô tả công");
        QuoteResponse v1Pending = quoteService.createInitialQuote(b.getId(), new CreateQuoteRequest("Chẩn đoán v1", List.of(item1)), providerUser);

        // 2. Khách duyệt v1 -> v1 thành ACCEPTED
        quoteService.acceptQuote(b.getId(), v1Pending.id(), customer);

        // 3. Thợ revise tạo v2 phát sinh (v1 ACCEPTED giữ nguyên, v2 PENDING dựa trên v1)
        CreateQuoteItemRequest item2 = new CreateQuoteItemRequest(QuoteItemType.PART, "Phụ tùng phát sinh", 2, new BigDecimal("75000"), "Thay thế mới");
        QuoteResponse v2Pending = quoteService.reviseQuote(b.getId(), new CreateQuoteRequest("Chẩn đoán v2", List.of(item1, item2)), providerUser);

        // 4. Khách duyệt v2 -> v1 ACCEPTED cũ chuyển sang SUPERSEDED, v2 chuyển sang ACCEPTED
        quoteService.acceptQuote(b.getId(), v2Pending.id(), customer);

        // Lấy lại cả 2 quote từ service
        QuoteResponse v1Superseded = quoteService.getQuoteDetails(b.getId(), v1Pending.id(), customer);
        QuoteResponse v2Accepted = quoteService.getQuoteDetails(b.getId(), v2Pending.id(), customer);

        assertEquals(QuoteStatus.SUPERSEDED, v1Superseded.status());
        assertEquals(QuoteStatus.ACCEPTED, v2Accepted.status());

        // Chuyển sang JSON Tree bằng Jackson ObjectMapper như response thực tế của Spring Boot
        com.fasterxml.jackson.databind.JsonNode jsonV1 = objectMapper.readTree(objectMapper.writeValueAsString(v1Superseded));
        com.fasterxml.jackson.databind.JsonNode jsonV2 = objectMapper.readTree(objectMapper.writeValueAsString(v2Accepted));

        // Kiểm tra Quote cũ (SUPERSEDED):
        // - Quote cha có trường status
        assertTrue(jsonV1.has("status"), "Quote cha phải có trường status");
        assertEquals("SUPERSEDED", jsonV1.get("status").asText());
        // - Mọi item trong mảng items KHÔNG ĐƯỢC có status hoặc effectiveStatus
        com.fasterxml.jackson.databind.JsonNode itemsV1 = jsonV1.get("items");
        assertNotNull(itemsV1);
        assertTrue(itemsV1.isArray() && !itemsV1.isEmpty());
        for (com.fasterxml.jackson.databind.JsonNode itemNode : itemsV1) {
            assertFalse(itemNode.has("status"), "Item trong quote SUPERSEDED tuyệt đối không có field status");
            assertFalse(itemNode.has("effectiveStatus"), "Item trong quote SUPERSEDED tuyệt đối không có field effectiveStatus");
            assertTrue(itemNode.has("name"), "Item phải có trường name");
            assertTrue(itemNode.has("quantity"), "Item phải có trường quantity");
            assertTrue(itemNode.has("unitPrice"), "Item phải có trường unitPrice");
            assertTrue(itemNode.has("amount"), "Item phải có trường amount");
        }

        // Kiểm tra Quote mới (ACCEPTED):
        // - Quote cha có trường status
        assertTrue(jsonV2.has("status"), "Quote cha phải có trường status");
        assertEquals("ACCEPTED", jsonV2.get("status").asText());
        // - Mọi item trong mảng items KHÔNG ĐƯỢC có status hoặc effectiveStatus
        com.fasterxml.jackson.databind.JsonNode itemsV2 = jsonV2.get("items");
        assertNotNull(itemsV2);
        assertTrue(itemsV2.isArray() && itemsV2.size() == 2);
        for (com.fasterxml.jackson.databind.JsonNode itemNode : itemsV2) {
            assertFalse(itemNode.has("status"), "Item trong quote ACCEPTED tuyệt đối không có field status");
            assertFalse(itemNode.has("effectiveStatus"), "Item trong quote ACCEPTED tuyệt đối không có field effectiveStatus");
            assertTrue(itemNode.has("name"), "Item phải có trường name");
            assertTrue(itemNode.has("quantity"), "Item phải có trường quantity");
            assertTrue(itemNode.has("unitPrice"), "Item phải có trường unitPrice");
            assertTrue(itemNode.has("amount"), "Item phải có trường amount");
        }

        // Kiểm tra schema thực tế trong Database:
        Integer statusColumnExists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'repair_quote_items' AND column_name = 'status'",
                Integer.class
        );
        log.info("Database schema check: repair_quote_items.status column count = {}", statusColumnExists);
    }
}
