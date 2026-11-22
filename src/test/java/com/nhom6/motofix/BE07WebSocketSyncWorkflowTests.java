package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.ConfirmPaymentRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.ProviderRevenueResponse;
import com.nhom6.motofix.dto.respond.SyncEventPayload;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.ProviderService;
import com.nhom6.motofix.websocket.BookingSyncApplicationEvent;
import com.nhom6.motofix.websocket.StompAuthChannelInterceptor;
import com.nhom6.motofix.websocket.WebSocketEventBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
public class BE07WebSocketSyncWorkflowTests {

    @Autowired
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Autowired
    private WebSocketEventBridge webSocketEventBridge;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RepairQuoteRepository quoteRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User customer1;
    private User customer2;
    private User providerUser1;
    private User providerUser2;
    private ProviderProfile providerProfile1;
    private ProviderProfile providerProfile2;
    private Booking booking1;

    private String customer1Token;
    private String customer2Token;
    private String provider1Token;
    private String provider2Token;

    private final MessageChannel mockChannel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        bookingService.setClock(Clock.systemDefaultZone());
        providerService.setClock(Clock.systemDefaultZone());
        webSocketEventBridge.clearCapturedEvents();

        // 1. Tạo Customer 1
        customer1 = new User();
        customer1.setFullName("Nguyen Van Khach 1");
        customer1.setEmail("cust1_" + UUID.randomUUID() + "@test.com");
        customer1.setPhone("098" + System.currentTimeMillis() % 10000000);
        customer1.setPasswordHash("hash");
        customer1.setCurrentRole("CUSTOMER");
        customer1.setStatus("active");
        customer1 = userRepository.save(customer1);
        customer1Token = jwtService.generateToken(customer1);

        // 2. Tạo Customer 2 (Người lạ)
        customer2 = new User();
        customer2.setFullName("Le Thi Khach 2");
        customer2.setEmail("cust2_" + UUID.randomUUID() + "@test.com");
        customer2.setPhone("097" + System.currentTimeMillis() % 10000000);
        customer2.setPasswordHash("hash");
        customer2.setCurrentRole("CUSTOMER");
        customer2.setStatus("active");
        customer2 = userRepository.save(customer2);
        customer2Token = jwtService.generateToken(customer2);

        // 3. Tạo Provider 1 (Thợ chính được gán)
        providerUser1 = new User();
        providerUser1.setFullName("Pham Van Tho 1");
        providerUser1.setEmail("prov1_" + UUID.randomUUID() + "@test.com");
        providerUser1.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser1.setPasswordHash("hash");
        providerUser1.setCurrentRole("PROVIDER");
        providerUser1.setStatus("active");
        providerUser1 = userRepository.save(providerUser1);
        provider1Token = jwtService.generateToken(providerUser1);

        providerProfile1 = new ProviderProfile();
        providerProfile1.setUser(providerUser1);
        providerProfile1.setBusinessName("Garage MotoFix 1");
        providerProfile1.setAddress("123 Le Loi, Quan 1, TP.HCM");
        providerProfile1.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile1.setActive(true);
        providerProfile1 = providerProfileRepository.save(providerProfile1);

        // 4. Tạo Provider 2 (Thợ khác không được gán)
        providerUser2 = new User();
        providerUser2.setFullName("Tran Van Tho 2");
        providerUser2.setEmail("prov2_" + UUID.randomUUID() + "@test.com");
        providerUser2.setPhone("092" + System.currentTimeMillis() % 10000000);
        providerUser2.setPasswordHash("hash");
        providerUser2.setCurrentRole("PROVIDER");
        providerUser2.setStatus("active");
        providerUser2 = userRepository.save(providerUser2);
        provider2Token = jwtService.generateToken(providerUser2);

        providerProfile2 = new ProviderProfile();
        providerProfile2.setUser(providerUser2);
        providerProfile2.setBusinessName("Garage MotoFix 2");
        providerProfile2.setAddress("456 Hai Ba Trung, Quan 3, TP.HCM");
        providerProfile2.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile2.setActive(true);
        providerProfile2 = providerProfileRepository.save(providerProfile2);

        // 5. Tạo Booking 1 thuộc về Customer 1 và Provider 1
        booking1 = new Booking();
        booking1.setCustomer(customer1);
        booking1.setProvider(providerProfile1);
        booking1.setStatus(BookingStatus.SELECTED_PROVIDER);
        booking1.setLatitude(10.776889);
        booking1.setLongitude(106.700806);
        booking1.setAddress("123 Le Loi, Ben Nghe, Quan 1, TP.HCM");
        booking1.setVehicleBrand("Honda");
        booking1.setVehicleModel("Air Blade");
        booking1.setVehicleLicensePlate("59-A1 12345");
        booking1.setProblemDescription("Xe chet may giua duong");
        booking1.setCreatedAt(LocalDateTime.now().minusHours(1));
        booking1 = bookingRepository.save(booking1);
    }

    private RepairQuote createAcceptedQuote(Booking b, BigDecimal amount) {
        RepairQuote quote = new RepairQuote();
        quote.setBooking(b);
        quote.setVersion(1);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setDiagnosisNote("Sua xe");
        quote.setTotalAmount(amount);
        quote.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        RepairQuoteItem item = new RepairQuoteItem();
        item.setName("Tien cong");
        item.setItemType(QuoteItemType.LABOR);
        item.setQuantity(1);
        item.setUnitPrice(amount);
        item.setAmount(amount);
        quote.addItem(item);

        RepairQuote saved = quoteRepository.save(quote);
        if (b.getQuotes() == null) {
            b.setQuotes(new java.util.ArrayList<>());
        }
        b.getQuotes().add(saved);
        return saved;
    }

    // =========================================================================
    // 1. XÁC THỰC KẾT NỐI WEBSOCKET (STOMP CONNECT)
    // =========================================================================

    @Test
    @DisplayName("BE07-01: STOMP CONNECT với JWT hợp lệ -> Xác thực thành công và thiết lập User Principal")
    void testStompConnect_ValidJwt_Authenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + customer1Token);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompAuthChannelInterceptor.preSend(message, mockChannel);
        assertNotNull(result);

        StompHeaderAccessor resAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertNotNull(resAccessor);
        assertNotNull(resAccessor.getUser());
        assertTrue(resAccessor.getUser() instanceof UsernamePasswordAuthenticationToken);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) resAccessor.getUser();
        User authenticatedUser = (User) auth.getPrincipal();
        assertEquals(customer1.getId(), authenticatedUser.getId());
    }

    @Test
    @DisplayName("BE07-02: STOMP CONNECT với Token sai / không hợp lệ -> Ném BadCredentialsException")
    void testStompConnect_InvalidToken_ThrowsBadCredentials() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid.jwt.token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(BadCredentialsException.class, () ->
                stompAuthChannelInterceptor.preSend(message, mockChannel));
    }

    @Test
    @DisplayName("BE07-03: STOMP CONNECT thiếu header Authorization -> Ném BadCredentialsException")
    void testStompConnect_MissingToken_ThrowsBadCredentials() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(BadCredentialsException.class, () ->
                stompAuthChannelInterceptor.preSend(message, mockChannel));
    }

    // =========================================================================
    // 2. PHÂN QUYỀN ĐĂNG KÝ KÊNH (STOMP SUBSCRIBE) - NGƯỜI NGOÀI KHÔNG ĐỌC ĐƯỢC
    // =========================================================================

    @Test
    @DisplayName("BE07-04: Khách hàng chủ đơn subscribe /topic/bookings/{id} của mình -> Cho phép")
    void testSubscribe_CustomerOwnBooking_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/bookings/" + booking1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(customer1, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompAuthChannelInterceptor.preSend(message, mockChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("BE07-05: Thợ được phân công subscribe /topic/bookings/{id} của đơn -> Cho phép")
    void testSubscribe_AssignedProvider_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/bookings/" + booking1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(providerUser1, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompAuthChannelInterceptor.preSend(message, mockChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("BE07-06: Khách hàng lạ (Customer 2) subscribe /topic/bookings/{id} của Customer 1 -> Bị từ chối 403 AccessDenied")
    void testSubscribe_StrangerCustomer_Denied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/bookings/" + booking1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(customer2, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(message, mockChannel));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("BE07-07: Thợ khác (Provider 2) chưa được gán subscribe /topic/bookings/{id} -> Bị từ chối 403 AccessDenied")
    void testSubscribe_UnassignedProvider_Denied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/bookings/" + booking1.getId());
        accessor.setUser(new UsernamePasswordAuthenticationToken(providerUser2, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(message, mockChannel));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("BE07-08: Thợ subscribe kênh offer của chính mình (/topic/providers/{id}/offers) -> Cho phép")
    void testSubscribe_ProviderOwnOffersTopic_Allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/providers/" + providerProfile1.getId() + "/offers");
        accessor.setUser(new UsernamePasswordAuthenticationToken(providerUser1, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompAuthChannelInterceptor.preSend(message, mockChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("BE07-09: Thợ A subscribe kênh offer của Thợ B -> Bị từ chối 403 AccessDenied")
    void testSubscribe_ProviderOtherOffersTopic_Denied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/providers/" + providerProfile2.getId() + "/offers");
        accessor.setUser(new UsernamePasswordAuthenticationToken(providerUser1, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(message, mockChannel));
    }

    @Test
    @DisplayName("BE07-09B: Admin cố tình subscribe topic cá nhân /topic/bookings/{id} hoặc /topic/providers/{id}/offers -> Bị từ chối 403 (Giới hạn admin đúng phạm vi duyệt hồ sơ)")
    void testSubscribe_AdminRestrictedFromPersonalTopics_Denied() {
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@motofix.test");
        adminUser.setCurrentRole("ADMIN");
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminUser.setRoles(Set.of(adminRole));

        // 1. Admin subscribe /topic/bookings/{bookingId} -> Bị chặn
        StompHeaderAccessor bookingAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        bookingAccessor.setDestination("/topic/bookings/" + booking1.getId());
        bookingAccessor.setUser(new UsernamePasswordAuthenticationToken(adminUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Message<byte[]> bookingMsg = MessageBuilder.createMessage(new byte[0], bookingAccessor.getMessageHeaders());

        AccessDeniedException ex1 = assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(bookingMsg, mockChannel));
        assertTrue(ex1.getMessage().contains("not authorized"));

        // 2. Admin subscribe /topic/providers/{id}/offers -> Bị chặn
        StompHeaderAccessor providerAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        providerAccessor.setDestination("/topic/providers/" + providerProfile1.getId() + "/offers");
        providerAccessor.setUser(new UsernamePasswordAuthenticationToken(adminUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Message<byte[]> provMsg = MessageBuilder.createMessage(new byte[0], providerAccessor.getMessageHeaders());

        AccessDeniedException ex2 = assertThrows(AccessDeniedException.class, () ->
                stompAuthChannelInterceptor.preSend(provMsg, mockChannel));
        assertTrue(ex2.getMessage().contains("not authorized"));
    }

    // =========================================================================
    // 3. PHÁT SỰ KIỆN SAU COMMIT & CHỐNG PHÁT KHI ROLLBACK
    // =========================================================================

    @Test
    @DisplayName("BE07-10: Giao dịch thành công (COMMIT) -> Sự kiện đồng bộ được phát tới WebSocket Bridge")
    void testEventPublished_OnTransactionCommit_DeliveredToBridge() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.execute(status -> {
            bookingService.confirmEnRoute(booking1.getId(), providerUser1);
            return null;
        });

        List<BookingSyncApplicationEvent> events = webSocketEventBridge.getCapturedEvents();
        assertFalse(events.isEmpty(), "Sự kiện phải được chuyển phát sau khi transaction commit thành công");
        BookingSyncApplicationEvent event = events.get(events.size() - 1);
        assertEquals("/topic/bookings/" + booking1.getId(), event.destination());
        assertEquals(SyncEventType.BOOKING_STATUS_CHANGED, event.payload().eventType());
        assertEquals(BookingStatus.PROVIDER_EN_ROUTE, event.payload().status());
    }

    @Test
    @DisplayName("BE07-11: Giao dịch bị ROLLBACK -> Tuyệt đối không phát sự kiện ra WebSocket Bridge")
    void testEventNotPublished_OnTransactionRollback() {
        webSocketEventBridge.clearCapturedEvents();
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () ->
                txTemplate.execute(status -> {
                    // Phát thử 1 sự kiện đồng bộ trong transaction
                    SyncEventPayload payload = SyncEventPayload.of(
                            SyncEventType.BOOKING_STATUS_CHANGED,
                            booking1.getId(),
                            BookingStatus.INSPECTING,
                            null,
                            providerUser1.getId(),
                            "Thử phát trước khi rollback",
                            null,
                            "/api/bookings/" + booking1.getId()
                    );
                    eventPublisher.publishEvent(new BookingSyncApplicationEvent("/topic/bookings/" + booking1.getId(), payload));

                    // Ném lỗi làm rollback transaction
                    throw new RuntimeException("Cố tình gây lỗi để rollback transaction!");
                })
        );

        // Kiểm tra danh sách sự kiện đã phát
        List<BookingSyncApplicationEvent> events = webSocketEventBridge.getCapturedEvents();
        assertTrue(events.isEmpty(), "Khi transaction bị rollback, tuyệt đối không được phát sự kiện ra WebSocket");
    }

    // =========================================================================
    // 4. XỬ LÝ SỰ KIỆN LẶP HOẶC MUỘN TRÊN CLIENT (MONOTONIC STATUS ORDER)
    // =========================================================================

    @Test
    @DisplayName("BE07-12: Thứ tự trạng thái (statusOrder) tăng đơn điệu theo tiến trình máy trạng thái")
    void testSyncPayload_MonotonicStatusOrder() {
        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.SEARCHING) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.SELECTED_PROVIDER));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.SELECTED_PROVIDER) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.PROVIDER_EN_ROUTE));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.PROVIDER_EN_ROUTE) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.INSPECTING));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.INSPECTING) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.IN_PROGRESS));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.IN_PROGRESS) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.WAITING_PAYMENT));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.WAITING_PAYMENT) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.COMPLETED));

        assertTrue(SyncEventPayload.calculateStatusOrder(BookingStatus.COMPLETED) <
                SyncEventPayload.calculateStatusOrder(BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("BE07-13: Mô phỏng Client nhận sự kiện muộn hoặc lặp -> Kiểm tra cơ chế chống lùi bước trạng thái & cập nhật nhiều offer trong cùng SEARCHING")
    void testStaleOrDuplicateEvent_DoesNotRegressStatus() {
        // Giả sử phía Client đang ở trạng thái COMPLETED (thứ tự 7)
        BookingStatus currentClientStatus = BookingStatus.COMPLETED;
        int currentClientOrder = SyncEventPayload.calculateStatusOrder(currentClientStatus);
        assertEquals(7, currentClientOrder);

        // Nhận được một sự kiện muộn WAITING_PAYMENT (thứ tự 6) do lag mạng
        SyncEventPayload stalePayload = SyncEventPayload.of(
                SyncEventType.BOOKING_COMPLETED_REPAIR,
                booking1.getId(),
                BookingStatus.WAITING_PAYMENT,
                1,
                providerUser1.getId(),
                "Thợ đã sửa xong",
                null,
                "/api/bookings/" + booking1.getId()
        );

        // Client kiểm tra: nếu event.statusOrder <= currentClientOrder thì bỏ qua
        boolean shouldDiscard = stalePayload.statusOrder() <= currentClientOrder;
        assertTrue(shouldDiscard, "Client phải phát hiện và bỏ qua sự kiện cũ hơn trạng thái hiện tại");

        // Nhận được sự kiện lặp cùng COMPLETED (cùng eventId hoặc cùng order)
        SyncEventPayload duplicatePayload = SyncEventPayload.of(
                SyncEventType.PAYMENT_CONFIRMED,
                booking1.getId(),
                BookingStatus.COMPLETED,
                1,
                providerUser1.getId(),
                "Đã nhận tiền",
                null,
                "/api/bookings/" + booking1.getId()
        );

        boolean isDuplicateOrder = duplicatePayload.statusOrder() <= currentClientOrder;
        assertTrue(isDuplicateOrder, "Sự kiện lặp lại không làm thay đổi trạng thái tiến trình của client");

        // KIỂM TRA QUY TẮC: Trong cùng trạng thái SEARCHING (thứ tự 1), nhiều offer thay đổi (T1 accept, T2 accept, T1 expire)
        // Client đang ở SEARCHING
        BookingStatus searchingStatus = BookingStatus.SEARCHING;
        int searchingOrder = SyncEventPayload.calculateStatusOrder(searchingStatus);
        assertEquals(1, searchingOrder);

        UUID offer1Id = UUID.randomUUID();
        UUID offer2Id = UUID.randomUUID();

        SyncEventPayload offer1Accepted = SyncEventPayload.of(
                SyncEventType.OFFER_ACCEPTED,
                booking1.getId(),
                offer1Id,
                BookingStatus.SEARCHING,
                null,
                providerUser1.getId(),
                "Thợ 1 đồng ý",
                offer1Id,
                "/api/bookings/" + booking1.getId() + "/offers"
        );

        SyncEventPayload offer2Accepted = SyncEventPayload.of(
                SyncEventType.OFFER_ACCEPTED,
                booking1.getId(),
                offer2Id,
                BookingStatus.SEARCHING,
                null,
                providerUser2.getId(),
                "Thợ 2 đồng ý",
                offer2Id,
                "/api/bookings/" + booking1.getId() + "/offers"
        );

        SyncEventPayload offer1Expired = SyncEventPayload.of(
                SyncEventType.OFFER_EXPIRED,
                booking1.getId(),
                offer1Id,
                BookingStatus.SEARCHING,
                null,
                null,
                "Thợ 1 tắt online -> offer 1 hết hạn",
                offer1Id,
                "/api/bookings/" + booking1.getId() + "/offers"
        );

        // Quy tắc client: Với các sự kiện OFFER_*, khi client vẫn ở SEARCHING (searchingOrder <= 1),
        // KHÔNG discard theo statusOrder, mà cập nhật danh sách offer (thêm offer 1, thêm offer 2, xóa offer 1)
        java.util.Set<UUID> visibleOffers = new java.util.HashSet<>();

        // Nhận offer 1
        if (offer1Accepted.eventType() == SyncEventType.OFFER_ACCEPTED && searchingOrder <= 1) {
            visibleOffers.add(offer1Accepted.offerId());
        }
        // Nhận offer 2
        if (offer2Accepted.eventType() == SyncEventType.OFFER_ACCEPTED && searchingOrder <= 1) {
            visibleOffers.add(offer2Accepted.offerId());
        }
        assertEquals(2, visibleOffers.size(), "Khách phải thấy đủ cả hai offer khi cả hai thợ đồng ý");

        // Nhận offer 1 expired (thợ 1 tắt online / logout)
        if (offer1Expired.eventType() == SyncEventType.OFFER_EXPIRED && searchingOrder <= 1) {
            visibleOffers.remove(offer1Expired.offerId());
        }
        assertEquals(1, visibleOffers.size(), "Sau khi thợ 1 tắt online/hết hạn, offer 1 phải biến mất khỏi danh sách của khách");
        assertTrue(visibleOffers.contains(offer2Id), "Offer của thợ 2 vẫn còn hiệu lực");
    }

    // =========================================================================
    // 5. RECONNECT & SNAPSHOT QUA HTTP KHÔNG NHÂN ĐÔI DOANH THU
    // =========================================================================

    @Test
    @DisplayName("BE07-14: Khi reconnect, Client lấy snapshot qua HTTP -> Trạng thái chính xác")
    void testReconnect_FetchesFreshSnapshot_StateAccurate() {
        // Đặt booking ở WAITING_PAYMENT
        booking1.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking1);
        createAcceptedQuote(booking1, BigDecimal.valueOf(200000));

        // Client reconnect và gọi HTTP GET /api/bookings/{id} để lấy lại snapshot
        BookingResponse snapshot = bookingService.getBookingById(booking1.getId(), customer1);
        assertNotNull(snapshot);
        assertEquals(BookingStatus.WAITING_PAYMENT, snapshot.status());
        assertEquals("Honda", snapshot.vehicleBrand());
        assertEquals(0, BigDecimal.valueOf(200000).compareTo(snapshot.totalAmount()));
    }

    @Test
    @DisplayName("BE07-15: Bấm nhận tiền lặp lại hoặc nhận sự kiện thanh toán lặp -> Không nhân đôi doanh thu")
    void testDuplicatePaymentEvents_DoNotDoubleCountRevenue() {
        booking1.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking1);
        createAcceptedQuote(booking1, BigDecimal.valueOf(180000));

        // 1. Lần đầu: Xác nhận thanh toán thành công
        bookingService.confirmPayment(booking1.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH), providerUser1);

        // 2. Lần hai (bấm lặp hoặc nhận lặp request): Bị từ chối 409 Conflict
        assertThrows(ConflictException.class, () ->
                bookingService.confirmPayment(booking1.getId(), new ConfirmPaymentRequest(PaymentMethod.CASH), providerUser1));

        // 3. Kiểm tra doanh thu trong ngày: Chính xác 180k, đúng 1 đơn, không bị nhân đôi thành 360k
        ProviderRevenueResponse revenue = providerService.getProviderRevenue(providerUser1, null);
        assertNotNull(revenue);
        assertEquals(0, BigDecimal.valueOf(180000).compareTo(revenue.totalRevenue()));
        assertEquals(1L, revenue.totalBookings());
    }

    @AfterEach
    void tearDown() {
        bookingService.setClock(Clock.systemDefaultZone());
        providerService.setClock(Clock.systemDefaultZone());
    }
}
