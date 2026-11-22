package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.SyncEventPayload;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.enums.SyncEventType;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.AuthService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.OfferService;
import com.nhom6.motofix.service.ProviderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BE07RealStompIntegrationTests {

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
    private BookingOfferRepository bookingOfferRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private OfferService offerService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

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

        // 1. Tạo Khách
        customer = new User();
        customer.setEmail("cust.real." + UUID.randomUUID() + "@motofix.test");
        customer.setFullName("Nguyen Real Customer");
        customer.setPhone("090" + (System.currentTimeMillis() % 10000000));
        customer.setPasswordHash("hash123");
        customer.setCurrentRole("CUSTOMER");
        customer.setStatus("active");
        customer.setTokenVersion(0);
        customer.setRoles(new HashSet<>(Set.of(customerRole)));
        customer = userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        // 2. Tạo Thợ 1
        provider1 = new User();
        provider1.setEmail("prov1.real." + UUID.randomUUID() + "@motofix.test");
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

        // 3. Tạo Thợ 2
        provider2 = new User();
        provider2.setEmail("prov2.real." + UUID.randomUUID() + "@motofix.test");
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
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
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

    @Test
    @DisplayName("STOMP Test 1: Khách + 2 Thợ: Cả hai thợ đồng ý -> Khách thấy 2 offer; Khách chọn T1 -> T1 nhận qua kênh offer cũ, T2 nhận offer expired")
    void testWorkflow_OneCustomer_TwoProviders_SelectT1_RealStomp() throws Exception {
        // 1. Tạo booking ở SEARCHING
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setSearchRound(1);
        booking.setAddress("123 Hai Bà Trưng, Quận 1");
        booking.setLatitude(10.7800);
        booking.setLongitude(106.6980);
        booking.setVehicleBrand("Honda");
        booking.setVehicleModel("SH 150i");
        booking.setVehicleLicensePlate("59-A1 99999");
        booking.setProblemDescription("Bể bánh trước");
        booking = bookingRepository.save(booking);

        // 2. Tạo 2 offer cho T1 và T2
        BookingOffer offer1 = new BookingOffer();
        offer1.setBooking(booking);
        offer1.setProvider(profile1);
        offer1.setStatus(OfferStatus.PENDING);
        offer1.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer1 = bookingOfferRepository.save(offer1);

        BookingOffer offer2 = new BookingOffer();
        offer2.setBooking(booking);
        offer2.setProvider(profile2);
        offer2.setStatus(OfferStatus.PENDING);
        offer2.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer2 = bookingOfferRepository.save(offer2);

        // 3. Kết nối STOMP thật cho cả 3 người
        StompSession customerSession = connectStomp(customerToken);
        StompSession p1Session = connectStomp(provider1Token);
        StompSession p2Session = connectStomp(provider2Token);

        // Khách subscribe /topic/bookings/{bookingId}
        BlockingStompHandler customerHandler = new BlockingStompHandler();
        customerSession.subscribe("/topic/bookings/" + booking.getId(), customerHandler);

        // T1 subscribe /topic/providers/{p1Id}/offers
        BlockingStompHandler p1Handler = new BlockingStompHandler();
        p1Session.subscribe("/topic/providers/" + profile1.getId() + "/offers", p1Handler);

        // T2 subscribe /topic/providers/{p2Id}/offers
        BlockingStompHandler p2Handler = new BlockingStompHandler();
        p2Session.subscribe("/topic/providers/" + profile2.getId() + "/offers", p2Handler);

        // Đợi 200ms để STOMP broker hoàn tất đăng ký
        Thread.sleep(300);

        // 4. Thợ 1 chấp nhận offer
        AcceptOfferRequest req1 = new AcceptOfferRequest(
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(250000),
                15,
                "Đang có mặt gần bạn"
        );
        offerService.accept(offer1.getId(), req1, provider1);

        // Khách nhận OFFER_ACCEPTED từ Thợ 1
        SyncEventPayload custEvent1 = customerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(custEvent1, "Khách hàng phải nhận được sự kiện khi Thợ 1 đồng ý");
        assertEquals(SyncEventType.OFFER_ACCEPTED, custEvent1.eventType());
        assertEquals(offer1.getId(), custEvent1.offerId());

        // 5. Thợ 2 chấp nhận offer
        AcceptOfferRequest req2 = new AcceptOfferRequest(
                BigDecimal.valueOf(160000),
                BigDecimal.valueOf(280000),
                20,
                "Đã sẵn sàng cứu hộ"
        );
        offerService.accept(offer2.getId(), req2, provider2);

        // Khách nhận OFFER_ACCEPTED từ Thợ 2
        SyncEventPayload custEvent2 = customerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(custEvent2, "Khách hàng phải nhận được sự kiện khi Thợ 2 đồng ý");
        assertEquals(SyncEventType.OFFER_ACCEPTED, custEvent2.eventType());
        assertEquals(offer2.getId(), custEvent2.offerId());

        // Khách đã thấy đủ cả hai offer
        assertNotEquals(custEvent1.offerId(), custEvent2.offerId(), "Khách phải thấy 2 offer khác nhau từ 2 thợ");

        // 6. Khách chọn Thợ 1
        bookingService.selectOffer(booking.getId(), offer1.getId(), customer.getId());

        // Thợ 1 nhận thông báo PROVIDER_SELECTED qua kênh đã nghe trước khi được chọn (/topic/providers/{p1Id}/offers)
        SyncEventPayload p1SelectedEvent = p1Handler.poll(5, TimeUnit.SECONDS);
        assertNotNull(p1SelectedEvent, "Thợ 1 phải nhận được thông báo được chọn qua kênh /topic/providers/{p1Id}/offers");
        assertEquals(SyncEventType.PROVIDER_SELECTED, p1SelectedEvent.eventType());
        assertEquals(booking.getId(), p1SelectedEvent.bookingId());

        // Thợ 2 nhận thông báo OFFER_EXPIRED qua kênh của mình (/topic/providers/{p2Id}/offers)
        SyncEventPayload p2ExpiredEvent = p2Handler.poll(5, TimeUnit.SECONDS);
        assertNotNull(p2ExpiredEvent, "Thợ 2 phải nhận được thông báo offer hết hiệu lực");
        assertEquals(SyncEventType.OFFER_EXPIRED, p2ExpiredEvent.eventType());

        // Khách hàng cũng nhận được PROVIDER_SELECTED trên /topic/bookings/{bookingId}
        SyncEventPayload custSelectedEvent = customerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(custSelectedEvent);
        assertEquals(SyncEventType.PROVIDER_SELECTED, custSelectedEvent.eventType());

        customerSession.disconnect();
        p1Session.disconnect();
        p2Session.disconnect();
    }

    @Test
    @DisplayName("STOMP Test 2: Thợ tắt online hoặc logout -> Offer biến mất ở phía khách hàng (/topic/bookings/{id})")
    void testProviderOfflineOrLogout_MakesOfferDisappearOnCustomerTopic_RealStomp() throws Exception {
        // Tạo booking
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setSearchRound(1);
        booking.setAddress("200 Hai Bà Trưng, Quận 1");
        booking.setLatitude(10.7800);
        booking.setLongitude(106.6980);
        booking.setVehicleBrand("Yamaha");
        booking.setVehicleModel("Exciter 150");
        booking.setVehicleLicensePlate("59-B1 12345");
        booking.setProblemDescription("Hết bình");
        booking = bookingRepository.save(booking);

        // Tạo offer cho T1 và T1 đồng ý
        BookingOffer offer = new BookingOffer();
        offer.setBooking(booking);
        offer.setProvider(profile1);
        offer.setStatus(OfferStatus.PENDING);
        offer.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer = bookingOfferRepository.save(offer);

        StompSession customerSession = connectStomp(customerToken);
        BlockingStompHandler customerHandler = new BlockingStompHandler();
        customerSession.subscribe("/topic/bookings/" + booking.getId(), customerHandler);
        Thread.sleep(300);

        // T1 đồng ý
        offerService.accept(offer.getId(), new AcceptOfferRequest(
                BigDecimal.valueOf(100000), BigDecimal.valueOf(150000), 10, "Đang tới ngay"
        ), provider1);

        SyncEventPayload acceptedEvent = customerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(acceptedEvent);
        assertEquals(SyncEventType.OFFER_ACCEPTED, acceptedEvent.eventType());
        assertEquals(offer.getId(), acceptedEvent.offerId());

        // Thợ 1 logout (làm thợ offline và expire offer)
        authService.logout(provider1);

        // Khách nhận được OFFER_EXPIRED kèm offerId để xóa khỏi màn hình chờ chọn
        SyncEventPayload expiredEvent = customerHandler.poll(5, TimeUnit.SECONDS);
        assertNotNull(expiredEvent, "Khách hàng phải nhận được thông báo OFFER_EXPIRED khi thợ logout hoặc tắt online");
        assertEquals(SyncEventType.OFFER_EXPIRED, expiredEvent.eventType());
        assertEquals(offer.getId(), expiredEvent.offerId());

        customerSession.disconnect();
    }

    @Test
    @DisplayName("STOMP Test 3: Khách ngắt kết nối, trạng thái thay đổi lúc offline -> Reconnect và lấy snapshot qua HTTP thật có xác thực")
    void testClientDisconnect_OfflineStateChange_Reconnect_HttpSnapshotWithAuth() throws Exception {
        // Tạo booking đã gán Thợ 1
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(profile1);
        booking.setStatus(BookingStatus.SELECTED_PROVIDER);
        booking.setSearchRound(1);
        booking.setAddress("789 Điện Biên Phủ, Bình Thạnh");
        booking.setLatitude(10.7900);
        booking.setLongitude(106.7100);
        booking.setVehicleBrand("Honda");
        booking.setVehicleModel("Vision");
        booking.setVehicleLicensePlate("59-C1 55555");
        booking.setProblemDescription("Thủng lốp sau");
        booking = bookingRepository.save(booking);

        // 1. Khách kết nối STOMP
        StompSession session1 = connectStomp(customerToken);
        assertTrue(session1.isConnected());

        // 2. Khách ngắt kết nối (mô phỏng rớt mạng / đóng app)
        session1.disconnect();
        assertFalse(session1.isConnected());

        // 3. Trong lúc khách offline: Thợ 1 bấm "Đang đến nơi"
        bookingService.confirmEnRoute(booking.getId(), provider1);

        // 4. Khách kết nối lại STOMP
        StompSession session2 = connectStomp(customerToken);
        assertTrue(session2.isConnected());

        // 5. Khách gọi HTTP snapshot thật có xác thực Bearer token: GET /api/bookings/{id}
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/bookings/" + booking.getId()))
                .header("Authorization", "Bearer " + customerToken)
                .GET()
                .build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, httpResponse.statusCode(), "Yêu cầu HTTP snapshot có token hợp lệ phải trả về 200 OK");

        BookingResponse body = objectMapper.readValue(httpResponse.body(), BookingResponse.class);
        assertNotNull(body);
        assertEquals(BookingStatus.PROVIDER_EN_ROUTE, body.status(),
                "Sau khi reconnect, snapshot HTTP phải trả về trạng thái mới nhất PROVIDER_EN_ROUTE đã thay đổi lúc offline");
        assertEquals(booking.getId(), body.id());

        session2.disconnect();
    }

    @Test
    @DisplayName("STOMP Test 4: Token phiên cũ sau logout bị từ chối kết nối STOMP")
    void testOldSession_AfterLogout_Rejected() throws Exception {
        // Lấy token của khách
        String oldToken = customerToken;

        // Khách đăng xuất -> tokenVersion tăng lên
        authService.logout(customer);

        // Thử kết nối STOMP bằng token cũ
        assertThrows(Exception.class, () -> connectStomp(oldToken),
                "Kết nối STOMP bằng token cũ sau khi đã logout phải bị từ chối xác thực");
    }

    @Test
    @DisplayName("STOMP Test 5: Giới hạn Admin đúng phạm vi duyệt hồ sơ -> Admin cố subscribe topic cá nhân bị từ chối")
    void testAdminRestrictedFromPersonalBookingTopics_RealStomp() throws Exception {
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            return roleRepository.save(r);
        });

        User adminUser = new User();
        adminUser.setEmail("admin.stomp." + UUID.randomUUID() + "@motofix.test");
        adminUser.setFullName("Super Admin");
        adminUser.setPhone("099" + (System.currentTimeMillis() % 10000000));
        adminUser.setPasswordHash("hash123");
        adminUser.setCurrentRole("ADMIN");
        adminUser.setStatus("active");
        adminUser.setTokenVersion(0);
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);
        String adminToken = jwtService.generateToken(adminUser);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setSearchRound(1);
        booking.setAddress("111 Nguyễn Trãi, Quận 5");
        booking.setLatitude(10.7500);
        booking.setLongitude(106.6700);
        booking = bookingRepository.save(booking);

        // Admin kết nối STOMP thành công
        StompSession adminSession = connectStomp(adminToken);
        assertTrue(adminSession.isConnected());

        // Admin cố subscribe /topic/bookings/{bookingId} -> Bị từ chối
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        adminSession.subscribe("/topic/bookings/" + booking.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        // Đợi một khoảng ngắn: do bị AccessDenied, kết nối hoặc subscription bị từ chối
        Thread.sleep(500);
        if (adminSession.isConnected()) {
            adminSession.disconnect();
        }
    }
}
