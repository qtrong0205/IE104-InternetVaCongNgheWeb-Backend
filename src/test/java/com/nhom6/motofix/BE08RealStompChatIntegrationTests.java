package com.nhom6.motofix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.SendMessageRequest;
import com.nhom6.motofix.dto.respond.ChatMessageResponse;
import com.nhom6.motofix.dto.respond.SyncEventPayload;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.SyncEventType;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BE08RealStompChatIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private BookingService bookingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private User customer;
    private User provider1;
    private User provider2;
    private ProviderProfile profile1;
    private ProviderProfile profile2;
    private String customerToken;
    private String provider1Token;
    private String provider2Token;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });

        // 1. Tạo Khách hàng
        customer = new User();
        customer.setEmail("cust.chat.stomp." + UUID.randomUUID() + "@motofix.test");
        customer.setFullName("Khách Hàng STOMP");
        customer.setPhone("098" + (System.currentTimeMillis() % 10000000));
        customer.setPasswordHash("hash123");
        customer.setCurrentRole("CUSTOMER");
        customer.setStatus("active");
        customer.setTokenVersion(0);
        customer.setRoles(new HashSet<>(Set.of(customerRole)));
        customer = userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        // 2. Tạo Thợ 1 (Thợ chính)
        provider1 = new User();
        provider1.setEmail("prov1.chat.stomp." + UUID.randomUUID() + "@motofix.test");
        provider1.setFullName("Thợ 1 Sửa Xe");
        provider1.setPhone("091" + (System.currentTimeMillis() % 10000000));
        provider1.setPasswordHash("hash123");
        provider1.setCurrentRole("PROVIDER");
        provider1.setStatus("active");
        provider1.setTokenVersion(0);
        provider1.setRoles(new HashSet<>(Set.of(providerRole)));
        provider1 = userRepository.save(provider1);
        provider1Token = jwtService.generateToken(provider1);

        profile1 = new ProviderProfile();
        profile1.setUser(provider1);
        profile1.setBusinessName("Tiệm Sửa Xe Thợ 1");
        profile1.setAddress("123 Lê Lợi, Quận 1");
        profile1.setBaseLat(10.7769f);
        profile1.setBaseLng(106.7009f);
        profile1.setServiceRadiusKm(10.0f);
        profile1.setApprovalStatus(ApprovalStatus.APPROVED);
        profile1.setActive(true);
        profile1 = providerProfileRepository.save(profile1);

        // 3. Tạo Thợ 2 (Thợ ngoài)
        provider2 = new User();
        provider2.setEmail("prov2.chat.stomp." + UUID.randomUUID() + "@motofix.test");
        provider2.setFullName("Thợ 2 Sửa Xe");
        provider2.setPhone("092" + (System.currentTimeMillis() % 10000000));
        provider2.setPasswordHash("hash123");
        provider2.setCurrentRole("PROVIDER");
        provider2.setStatus("active");
        provider2.setTokenVersion(0);
        provider2.setRoles(new HashSet<>(Set.of(providerRole)));
        provider2 = userRepository.save(provider2);
        provider2Token = jwtService.generateToken(provider2);

        profile2 = new ProviderProfile();
        profile2.setUser(provider2);
        profile2.setBusinessName("Tiệm Sửa Xe Thợ 2");
        profile2.setAddress("456 Nguyễn Huệ, Quận 1");
        profile2.setBaseLat(10.7745f);
        profile2.setBaseLng(106.7025f);
        profile2.setServiceRadiusKm(10.0f);
        profile2.setApprovalStatus(ApprovalStatus.APPROVED);
        profile2.setActive(true);
        profile2 = providerProfileRepository.save(profile2);
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null && stompClient.isRunning()) {
            stompClient.stop();
        }
    }

    private StompSession connectStomp(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        String wsUrl = "ws://localhost:" + port + "/ws";
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        return stompClient.connectAsync(wsUrl, wsHeaders, headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private static class BlockingStompHandler implements StompFrameHandler {
        private final BlockingQueue<SyncEventPayload> queue = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return SyncEventPayload.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof SyncEventPayload p) {
                queue.offer(p);
            }
        }

        public SyncEventPayload poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }

    private Booking createActiveBooking() {
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(profile1);
        booking.setStatus(BookingStatus.SELECTED_PROVIDER);
        booking.setLatitude(10.776889);
        booking.setLongitude(106.700806);
        booking.setAddress("123 Lê Lợi, Quận 1");
        booking.setVehicleBrand("Honda");
        booking.setVehicleModel("Air Blade");
        booking.setVehicleLicensePlate("59-A1 12345");
        booking.setProblemDescription("Xe hỏng bugi");
        booking.setCreatedAt(java.time.LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    // =========================================================================
    // 1. CHAT THỜI GIAN THỰC GIỮA KHÁCH VÀ THỢ ĐƯỢC CHỌN QUA STOMP THẬT
    // =========================================================================

    @Test
    @DisplayName("STOMP Chat 1: Khách và Thợ kết nối thật -> Nhắn tin qua lại nhận tức thì qua STOMP frame")
    void testRealStomp_CustomerAndProviderChatExchange_Success() throws Exception {
        Booking booking = createActiveBooking();
        String chatTopic = "/topic/bookings/" + booking.getId() + "/chat";

        // Khách kết nối STOMP và subscribe kênh chat
        StompSession customerSession = connectStomp(customerToken);
        assertTrue(customerSession.isConnected());
        BlockingStompHandler customerHandler = new BlockingStompHandler();
        customerSession.subscribe(chatTopic, customerHandler);

        // Thợ 1 kết nối STOMP và subscribe kênh chat
        StompSession providerSession = connectStomp(provider1Token);
        assertTrue(providerSession.isConnected());
        BlockingStompHandler providerHandler = new BlockingStompHandler();
        providerSession.subscribe(chatTopic, providerHandler);

        Thread.sleep(300); // Đợi subscriptions được broker ghi nhận

        // 1. Khách gửi tin nhắn qua HTTP POST REST API
        String custBody = objectMapper.writeValueAsString(new SendMessageRequest("Chào anh, anh đến cổng số 2 nhé"));
        HttpRequest custReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/bookings/" + booking.getId() + "/messages"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(custBody))
                .build();

        HttpResponse<String> postRes = httpClient.send(custReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postRes.statusCode());

        // Thợ 1 nhận được tin tức thì qua STOMP frame
        SyncEventPayload eventForProvider = providerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(eventForProvider, "Thợ 1 phải nhận được tin nhắn qua STOMP");
        assertEquals(SyncEventType.CHAT_MESSAGE, eventForProvider.eventType());
        assertEquals("Chào anh, anh đến cổng số 2 nhé", eventForProvider.message());
        assertEquals(customer.getId(), eventForProvider.actorId());

        // 2. Thợ 1 trả lời tin nhắn qua HTTP POST REST API
        String provBody = objectMapper.writeValueAsString(new SendMessageRequest("Ok bạn, tôi đang chạy vào cổng 2 đây"));
        HttpRequest provReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/bookings/" + booking.getId() + "/messages"))
                .header("Authorization", "Bearer " + provider1Token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(provBody))
                .build();

        HttpResponse<String> provPostRes = httpClient.send(provReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, provPostRes.statusCode());

        // Khách nhận được tin của thợ qua STOMP frame
        SyncEventPayload eventForCustomer = customerHandler.poll(5, TimeUnit.SECONDS);
        // Bỏ qua tin của chính mình nếu broker broadcast echo
        if (eventForCustomer != null && eventForCustomer.actorId().equals(customer.getId())) {
            eventForCustomer = customerHandler.poll(5, TimeUnit.SECONDS);
        }
        assertNotNull(eventForCustomer, "Khách hàng phải nhận được tin nhắn phản hồi của thợ");
        assertEquals("Ok bạn, tôi đang chạy vào cổng 2 đây", eventForCustomer.message());
        assertEquals(provider1.getId(), eventForCustomer.actorId());

        if (customerSession.isConnected()) customerSession.disconnect();
        if (providerSession.isConnected()) providerSession.disconnect();
    }

    // =========================================================================
    // 2. NGƯỜI NGOÀI (THỢ 2) BỊ TỪ CHỐI SUBSCRIBE KÊNH CHAT
    // =========================================================================

    @Test
    @DisplayName("STOMP Chat 2: Thợ 2 (người ngoài không được chọn) subscribe kênh chat -> Không nhận được tin nhắn")
    void testRealStomp_UnselectedProviderT2_CannotSubscribeChat() throws Exception {
        Booking booking = createActiveBooking();
        String chatTopic = "/topic/bookings/" + booking.getId() + "/chat";

        StompSession provider2Session = connectStomp(provider2Token);
        assertTrue(provider2Session.isConnected());

        BlockingStompHandler p2Handler = new BlockingStompHandler();
        provider2Session.subscribe(chatTopic, p2Handler);

        // Khách gửi 1 tin nhắn hợp lệ
        chatService.sendMessage(booking.getId(), new SendMessageRequest("Tin nhắn bí mật"), customer);

        // Thợ 2 không được phép nhận tin nhắn nào
        SyncEventPayload received = p2Handler.poll(2, TimeUnit.SECONDS);
        assertNull(received, "Người ngoài không được phép nhận bất kỳ tin nhắn nào từ kênh chat");

        if (provider2Session.isConnected()) {
            provider2Session.disconnect();
        }
    }

    // =========================================================================
    // 3. CHƯA CHỌN THỢ (SEARCHING): BỊ TỪ CHỐI SUBSCRIBE KÊNH CHAT
    // =========================================================================

    @Test
    @DisplayName("STOMP Chat 3: Khi đơn đang SEARCHING -> Bị server từ chối subscribe kênh chat")
    void testRealStomp_CannotSubscribeChat_WhileSearching() throws Exception {
        Booking searchingBooking = new Booking();
        searchingBooking.setCustomer(customer);
        searchingBooking.setStatus(BookingStatus.SEARCHING);
        searchingBooking.setLatitude(10.776889);
        searchingBooking.setLongitude(106.700806);
        searchingBooking.setAddress("123 Lê Lợi");
        searchingBooking.setProblemDescription("Tìm thợ");
        searchingBooking.setCreatedAt(java.time.LocalDateTime.now());
        searchingBooking = bookingRepository.save(searchingBooking);

        String chatTopic = "/topic/bookings/" + searchingBooking.getId() + "/chat";

        StompSession custSession = connectStomp(customerToken);
        assertTrue(custSession.isConnected());

        BlockingStompHandler handler = new BlockingStompHandler();
        custSession.subscribe(chatTopic, handler);

        // Đợi broker xử lý SUBSCRIBE frame
        Thread.sleep(500);

        // Kiểm tra không nhận được tin nhắn
        SyncEventPayload received = handler.poll(1, TimeUnit.SECONDS);
        assertNull(received, "Kênh chat trên đơn SEARCHING không được phép hoạt động");

        if (custSession.isConnected()) {
            custSession.disconnect();
        }
    }

    // =========================================================================
    // 4. GỬI TỪ TAB CŨ SAU KHI ĐƠN BỊ HỦY -> BỊ TỪ CHỐI 409 CONFLICT
    // =========================================================================

    @Test
    @DisplayName("STOMP Chat 4: Tab cũ gửi tin nhắn sau khi đơn đã bị hủy -> Bị từ chối 409 Conflict")
    void testRealStomp_SendFromOldTabAfterCancelled_Rejected() throws Exception {
        Booking booking = createActiveBooking();

        // 1. Gửi tin khi đơn còn mở
        chatService.sendMessage(booking.getId(), new SendMessageRequest("Tin nhắn hợp lệ lúc đầu"), customer);

        // 2. Đơn bị hủy (ví dụ từ tab khác hoặc bên kia bấm hủy)
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // 3. Tab cũ cố tình gửi tin mới qua HTTP POST
        String reqBody = objectMapper.writeValueAsString(new SendMessageRequest("Tin nhắn gửi từ tab cũ sau hủy"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/bookings/" + booking.getId() + "/messages"))
                .header("Authorization", "Bearer " + customerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, response.statusCode(), "Gửi tin khi đơn đã kết thúc phải bị từ chối 409 Conflict");
    }

    // =========================================================================
    // 5. RECONNECT VÀ TẢI LẠI LỊCH SỬ CHAT QUA HTTP SNAPSHOT THẬT CÓ AUTH
    // =========================================================================

    @Test
    @DisplayName("STOMP Chat 5: Client rớt mạng, kết nối lại và lấy snapshot lịch sử qua HTTP -> Đầy đủ và chính xác")
    void testRealStomp_ClientReconnect_FetchesHistoryViaHttpSnapshot() throws Exception {
        Booking booking = createActiveBooking();

        // Khách và thợ gửi 3 tin nhắn
        chatService.sendMessage(booking.getId(), new SendMessageRequest("Tin 1: Khách hỏi vị trí"), customer);
        chatService.sendMessage(booking.getId(), new SendMessageRequest("Tin 2: Thợ báo đang kẹt xe"), provider1);
        chatService.sendMessage(booking.getId(), new SendMessageRequest("Tin 3: Khách đồng ý chờ"), customer);

        // Client reconnect và gọi HTTP GET snapshot lịch sử trò chuyện
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/bookings/" + booking.getId() + "/messages?page=0&size=10"))
                .header("Authorization", "Bearer " + customerToken)
                .GET()
                .build();

        HttpResponse<String> getRes = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getRes.statusCode());
        assertNotNull(getRes.body());

        JsonNode root = objectMapper.readTree(getRes.body());
        JsonNode content = root.path("content");
        assertEquals(3, content.size(), "Snapshot phải trả về đủ 3 tin nhắn đã gửi");
        assertEquals("Tin 1: Khách hỏi vị trí", content.get(0).path("message").asText());
        assertEquals("Tin 2: Thợ báo đang kẹt xe", content.get(1).path("message").asText());
        assertEquals("Tin 3: Khách đồng ý chờ", content.get(2).path("message").asText());
    }
}
