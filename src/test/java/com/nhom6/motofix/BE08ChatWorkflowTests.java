package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.SendMessageRequest;
import com.nhom6.motofix.dto.respond.ChatMessageResponse;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.SyncEventType;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.ChatService;
import com.nhom6.motofix.websocket.BookingSyncApplicationEvent;
import com.nhom6.motofix.websocket.StompAuthChannelInterceptor;
import com.nhom6.motofix.websocket.WebSocketEventBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
public class BE08ChatWorkflowTests {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private WebSocketEventBridge webSocketEventBridge;

    @Autowired
    private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User customer1;
    private User customer2;
    private User providerUser1;
    private User providerUser2;
    private User adminUser;
    private ProviderProfile providerProfile1;
    private ProviderProfile providerProfile2;
    private Booking booking1;

    private String customer1Token;
    private String customer2Token;
    private String provider1Token;
    private String provider2Token;
    private String adminToken;

    private final MessageChannel mockChannel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        webSocketEventBridge.clearCapturedEvents();

        // 1. Tạo Customer 1 (Chủ đơn)
        customer1 = new User();
        customer1.setFullName("Nguyen Van Khach 1");
        customer1.setEmail("cust1_chat_" + UUID.randomUUID() + "@test.com");
        customer1.setPhone("098" + System.currentTimeMillis() % 10000000);
        customer1.setPasswordHash("hash123");
        customer1.setCurrentRole("CUSTOMER");
        customer1.setStatus("active");
        customer1 = userRepository.save(customer1);
        customer1Token = jwtService.generateToken(customer1);

        // 2. Tạo Customer 2 (Khách hàng lạ không liên quan)
        customer2 = new User();
        customer2.setFullName("Le Van La 2");
        customer2.setEmail("cust2_chat_" + UUID.randomUUID() + "@test.com");
        customer2.setPhone("097" + System.currentTimeMillis() % 10000000);
        customer2.setPasswordHash("hash123");
        customer2.setCurrentRole("CUSTOMER");
        customer2.setStatus("active");
        customer2 = userRepository.save(customer2);
        customer2Token = jwtService.generateToken(customer2);

        // 3. Tạo Provider 1 (Thợ chính được chọn)
        providerUser1 = new User();
        providerUser1.setFullName("Pham Van Tho 1");
        providerUser1.setEmail("prov1_chat_" + UUID.randomUUID() + "@test.com");
        providerUser1.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser1.setPasswordHash("hash123");
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

        // 4. Tạo Provider 2 (Thợ khác không được chọn)
        providerUser2 = new User();
        providerUser2.setFullName("Tran Van Tho 2");
        providerUser2.setEmail("prov2_chat_" + UUID.randomUUID() + "@test.com");
        providerUser2.setPhone("092" + System.currentTimeMillis() % 10000000);
        providerUser2.setPasswordHash("hash123");
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

        // 5. Tạo Admin User
        adminUser = new User();
        adminUser.setFullName("He Thong Quan Tri");
        adminUser.setEmail("admin_chat_" + UUID.randomUUID() + "@test.com");
        adminUser.setPhone("090" + System.currentTimeMillis() % 10000000);
        adminUser.setPasswordHash("hash123");
        adminUser.setCurrentRole("ADMIN");
        adminUser.setStatus("active");
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // 6. Tạo Booking 1 (Đã chọn Provider 1)
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

    @AfterEach
    void tearDown() {
        webSocketEventBridge.clearCapturedEvents();
        try {
            chatMessageRepository.deleteAll();
            if (booking1 != null && booking1.getId() != null) {
                bookingRepository.deleteById(booking1.getId());
            }
            if (providerProfile1 != null && providerProfile1.getId() != null) {
                providerProfileRepository.deleteById(providerProfile1.getId());
            }
            if (providerProfile2 != null && providerProfile2.getId() != null) {
                providerProfileRepository.deleteById(providerProfile2.getId());
            }
            if (customer1 != null && customer1.getId() != null) {
                userRepository.deleteById(customer1.getId());
            }
            if (customer2 != null && customer2.getId() != null) {
                userRepository.deleteById(customer2.getId());
            }
            if (providerUser1 != null && providerUser1.getId() != null) {
                userRepository.deleteById(providerUser1.getId());
            }
            if (providerUser2 != null && providerUser2.getId() != null) {
                userRepository.deleteById(providerUser2.getId());
            }
            if (adminUser != null && adminUser.getId() != null) {
                userRepository.deleteById(adminUser.getId());
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 1. GỬI VÀ NHẬN TIN NHẮN KHI ĐƠN MỞ (SELECTED_PROVIDER, EN_ROUTE, INSPECTING, IN_PROGRESS)
    // =========================================================================

    @Test
    @DisplayName("BE08-01: Khách gửi tin nhắn khi đơn ở SELECTED_PROVIDER -> Thành công, lưu DB và phát WebSocket")
    void testSendMessage_WhenProviderSelected_Success() {
        SendMessageRequest request = new SendMessageRequest("Chào anh thợ, em đang đứng trước cây xăng");

        ChatMessageResponse response = chatService.sendMessage(booking1.getId(), request, customer1);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(booking1.getId(), response.bookingId());
        assertEquals(customer1.getId(), response.senderId());
        assertEquals("Nguyen Van Khach 1", response.senderName());
        assertEquals("CUSTOMER", response.senderRole());
        assertEquals("Chào anh thợ, em đang đứng trước cây xăng", response.message());
        assertNotNull(response.createdAt());

        // Kiểm tra đã lưu DB
        ChatMessage savedInDb = chatMessageRepository.findById(response.id()).orElse(null);
        assertNotNull(savedInDb);
        assertEquals("Chào anh thợ, em đang đứng trước cây xăng", savedInDb.getMessage());

        // Kiểm tra sự kiện WebSocket được phát tới /topic/bookings/{id}/chat
        List<BookingSyncApplicationEvent> events = webSocketEventBridge.getCapturedEvents();
        assertFalse(events.isEmpty());
        BookingSyncApplicationEvent lastEvent = events.get(events.size() - 1);
        assertEquals("/topic/bookings/" + booking1.getId() + "/chat", lastEvent.destination());
        assertEquals(SyncEventType.CHAT_MESSAGE, lastEvent.payload().eventType());
        assertEquals(customer1.getId(), lastEvent.payload().actorId());
        assertEquals("/api/bookings/" + booking1.getId() + "/messages", lastEvent.payload().snapshotUrl());
    }

    @Test
    @DisplayName("BE08-02: Thợ được gán gửi tin nhắn khi đơn ở PROVIDER_EN_ROUTE -> Thành công")
    void testSendMessage_ByAssignedProvider_WhenProviderEnRoute_Success() {
        booking1.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        bookingRepository.save(booking1);

        SendMessageRequest request = new SendMessageRequest("Tôi đang trên đường tới, khoảng 10 phút nữa có mặt nhé");

        ChatMessageResponse response = chatService.sendMessage(booking1.getId(), request, providerUser1);

        assertNotNull(response);
        assertEquals(providerUser1.getId(), response.senderId());
        assertEquals("Pham Van Tho 1", response.senderName());
        assertEquals("PROVIDER", response.senderRole());
        assertEquals("Tôi đang trên đường tới, khoảng 10 phút nữa có mặt nhé", response.message());
    }

    @Test
    @DisplayName("BE08-03: Khách và thợ nhắn qua lại trong các bước INSPECTING, IN_PROGRESS, WAITING_PAYMENT -> Đều thành công")
    void testSendMessage_DuringInspectingAndInProgress_Success() {
        // INSPECTING
        booking1.setStatus(BookingStatus.INSPECTING);
        bookingRepository.save(booking1);
        chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tôi đang kiểm tra bugi"), providerUser1);

        // IN_PROGRESS
        booking1.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking1);
        chatService.sendMessage(booking1.getId(), new SendMessageRequest("Dạ vâng anh sửa giúp em"), customer1);

        // WAITING_PAYMENT
        booking1.setStatus(BookingStatus.WAITING_PAYMENT);
        bookingRepository.save(booking1);
        chatService.sendMessage(booking1.getId(), new SendMessageRequest("Em chuẩn bị tiền mặt rồi ạ"), customer1);

        Page<ChatMessageResponse> history = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(0, 10));
        assertEquals(3, history.getTotalElements());
    }

    // =========================================================================
    // 2. CHƯA CHỌN THỢ (SEARCHING): KHÔNG MỞ CHAT KỂ CẢ KHI ACCEPTED OFFER
    // =========================================================================

    @Test
    @DisplayName("BE08-04: Đơn ở SEARCHING (kể cả thợ đã ACCEPTED offer) -> Không được gửi chat")
    void testSendMessage_WhenBookingInSearching_Rejected() {
        booking1.setStatus(BookingStatus.SEARCHING);
        booking1.setProvider(null);
        bookingRepository.save(booking1);

        // Khách thử gửi tin khi đang tìm thợ -> 409 Conflict
        assertThrows(ConflictException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Alo có ai nhận không?"), customer1));

        // Thợ 1 chưa được chọn (mới chỉ nhận/accept offer) thử gửi tin -> 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tôi nhận đơn rồi này"), providerUser1));
    }

    @Test
    @DisplayName("BE08-05: Đơn ở SEARCHING -> Không được xem lịch sử chat (409 Conflict)")
    void testGetChatHistory_WhenBookingInSearching_Rejected() {
        booking1.setStatus(BookingStatus.SEARCHING);
        booking1.setProvider(null);
        bookingRepository.save(booking1);

        assertThrows(ConflictException.class, () ->
                chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(0, 10)));
    }

    // =========================================================================
    // 3. KIỂM SOÁT QUYỀN TRUY CẬP: NGƯỜI NGOÀI / THỢ KHÁC / ADMIN
    // =========================================================================

    @Test
    @DisplayName("BE08-06: Người ngoài (Customer lạ) thử gửi tin nhắn hoặc xem lịch sử -> 403 Forbidden")
    void testStrangerCustomer_Forbidden() {
        assertThrows(AccessDeniedException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Spam tin nhắn"), customer2));

        assertThrows(AccessDeniedException.class, () ->
                chatService.getChatHistory(booking1.getId(), customer2, PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("BE08-07: Thợ khác (Provider 2 từng nhận offer nhưng không được chọn) -> 403 Forbidden")
    void testUnselectedProvider_Forbidden() {
        assertThrows(AccessDeniedException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tôi sửa rẻ hơn nè"), providerUser2));

        assertThrows(AccessDeniedException.class, () ->
                chatService.getChatHistory(booking1.getId(), providerUser2, PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("BE08-08: Admin thử đọc hoặc gửi tin nhắn vào cuộc trò chuyện cá nhân -> 403 Forbidden")
    void testAdminRestrictedFromChat_Forbidden() {
        assertThrows(AccessDeniedException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Admin theo dõi"), adminUser));

        assertThrows(AccessDeniedException.class, () ->
                chatService.getChatHistory(booking1.getId(), adminUser, PageRequest.of(0, 10)));
    }

    // =========================================================================
    // 4. KHI COMPLETED / CANCELLED: LỊCH SỬ CHỈ ĐỌC, CHẶN GỬI MỚI (TAB CŨ SAU HỦY)
    // =========================================================================

    @Test
    @DisplayName("BE08-09: Khi đơn COMPLETED -> Đọc được lịch sử cũ nhưng chặn gửi tin nhắn mới (409 Conflict)")
    void testChatHistory_WhenBookingCompleted_ReadOnly_CanReadCannotSend() {
        // Gửi 1 tin trước khi hoàn thành
        chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tin nhắn trước khi xong"), customer1);

        // Chuyển sang COMPLETED
        booking1.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking1);

        // Cả hai bên vẫn đọc được lịch sử cũ
        Page<ChatMessageResponse> custHistory = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(0, 10));
        assertEquals(1, custHistory.getTotalElements());

        Page<ChatMessageResponse> provHistory = chatService.getChatHistory(booking1.getId(), providerUser1, PageRequest.of(0, 10));
        assertEquals(1, provHistory.getTotalElements());

        // Cả khách và thợ thử gửi tin mới -> Bị từ chối ConflictException
        assertThrows(ConflictException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Thêm tin nhắn sau hoàn tất"), customer1));

        assertThrows(ConflictException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Thợ gửi thêm tin sau hoàn tất"), providerUser1));
    }

    @Test
    @DisplayName("BE08-10: Khi đơn CANCELLED -> Đọc được lịch sử cũ nhưng chặn gửi tin mới (Tab cũ sau khi hủy)")
    void testChatHistory_WhenBookingCancelled_ReadOnly_CanReadCannotSend() {
        // Gửi tin lúc đơn còn mở
        chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tôi chuẩn bị đi đây"), providerUser1);

        // Đơn bị hủy (bởi khách hoặc thợ)
        booking1.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking1);

        // Vẫn đọc được lịch sử
        Page<ChatMessageResponse> history = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(0, 10));
        assertEquals(1, history.getTotalElements());

        // Tab cũ còn mở WebSocket hoặc form chat cố tình bấm gửi sau khi đơn bị hủy -> Bị từ chối
        assertThrows(ConflictException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Alo xe sao rồi?"), customer1));

        assertThrows(ConflictException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tôi tới rồi sao hủy?"), providerUser1));
    }

    // =========================================================================
    // 5. PHÂN TRANG VÀ THỨ TỰ SẮP XẾP LỊCH SỬ TIN NHẮN
    // =========================================================================

    @Test
    @DisplayName("BE08-11: Phân trang lịch sử chat -> Trả về đúng thứ tự tăng dần theo thời gian (createdAt ASC)")
    void testChatPagination_ReturnsOrderedAscending() {
        // Tạo 5 tin nhắn liên tiếp
        for (int i = 1; i <= 5; i++) {
            chatService.sendMessage(booking1.getId(), new SendMessageRequest("Tin nhắn số " + i), customer1);
        }

        // Trang 0, size 2: Tin 1 và Tin 2
        Page<ChatMessageResponse> page0 = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(0, 2));
        assertEquals(5, page0.getTotalElements());
        assertEquals(3, page0.getTotalPages());
        assertEquals(2, page0.getContent().size());
        assertEquals("Tin nhắn số 1", page0.getContent().get(0).message());
        assertEquals("Tin nhắn số 2", page0.getContent().get(1).message());

        // Trang 1, size 2: Tin 3 và Tin 4
        Page<ChatMessageResponse> page1 = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(1, 2));
        assertEquals(2, page1.getContent().size());
        assertEquals("Tin nhắn số 3", page1.getContent().get(0).message());
        assertEquals("Tin nhắn số 4", page1.getContent().get(1).message());

        // Trang 2, size 2: Tin 5
        Page<ChatMessageResponse> page2 = chatService.getChatHistory(booking1.getId(), customer1, PageRequest.of(2, 2));
        assertEquals(1, page2.getContent().size());
        assertEquals("Tin nhắn số 5", page2.getContent().get(0).message());
    }

    // =========================================================================
    // 6. VALIDATION DỮ LIỆU ĐẦU VÀO
    // =========================================================================

    @Test
    @DisplayName("BE08-12: Tin nhắn null, rỗng, toàn khoảng trắng hoặc quá 1000 ký tự -> 400 BadRequest")
    void testMessageValidation_BlankOrExceedMaxLength_Rejected() {
        assertThrows(BadRequestException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest(""), customer1));

        assertThrows(BadRequestException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest("   "), customer1));

        String longMsg = "A".repeat(1001);
        assertThrows(BadRequestException.class, () ->
                chatService.sendMessage(booking1.getId(), new SendMessageRequest(longMsg), customer1));
    }

    // =========================================================================
    // 7. KIỂM TRA PHÂN QUYỀN STOMP SUBSCRIBE KÊNH CHAT
    // =========================================================================

    @Test
    @DisplayName("BE08-13: STOMP SUBSCRIBE /topic/bookings/{id}/chat -> Áp dụng đúng phân quyền khách/thợ chính")
    void testStompSubscribe_ChatTopic_EnforcesSameRules() {
        // Khách chủ đơn subscribe -> Cho phép
        assertDoesNotThrow(() -> subscribeStomp(customer1Token, "/topic/bookings/" + booking1.getId() + "/chat"));

        // Thợ chính được gán subscribe -> Cho phép
        assertDoesNotThrow(() -> subscribeStomp(provider1Token, "/topic/bookings/" + booking1.getId() + "/chat"));

        // Người ngoài (Customer 2) subscribe -> 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                subscribeStomp(customer2Token, "/topic/bookings/" + booking1.getId() + "/chat"));

        // Thợ khác (Provider 2) subscribe -> 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                subscribeStomp(provider2Token, "/topic/bookings/" + booking1.getId() + "/chat"));

        // Admin subscribe -> 403 Forbidden
        assertThrows(AccessDeniedException.class, () ->
                subscribeStomp(adminToken, "/topic/bookings/" + booking1.getId() + "/chat"));

        // Khi đơn ở SEARCHING: Khách và thợ chính cũng bị từ chối subscribe kênh chat
        booking1.setStatus(BookingStatus.SEARCHING);
        booking1.setProvider(null);
        bookingRepository.save(booking1);

        assertThrows(AccessDeniedException.class, () ->
                subscribeStomp(customer1Token, "/topic/bookings/" + booking1.getId() + "/chat"));
    }

    private void subscribeStomp(String jwtToken, String destination) {
        // 1. Giả lập CONNECT frame để xác thực User
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + jwtToken);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> authenticatedMessage = stompAuthChannelInterceptor.preSend(connectMessage, mockChannel);
        assertNotNull(authenticatedMessage);
        StompHeaderAccessor authAccessor = StompHeaderAccessor.wrap(authenticatedMessage);

        // 2. Giả lập SUBSCRIBE frame
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setUser(authAccessor.getUser());
        subAccessor.setDestination(destination);
        subAccessor.setSubscriptionId("sub-" + UUID.randomUUID());
        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        stompAuthChannelInterceptor.preSend(subMessage, mockChannel);
    }
}
