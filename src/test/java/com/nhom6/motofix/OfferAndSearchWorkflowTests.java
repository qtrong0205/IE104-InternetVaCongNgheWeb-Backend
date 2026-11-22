package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.request.DeclineOfferRequest;
import com.nhom6.motofix.dto.request.SearchRoundRequest;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.OfferService;
import com.nhom6.motofix.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class OfferAndSearchWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingOfferRepository bookingOfferRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private OfferService offerService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User customer1;
    private User customer2;
    private String customer1Token;
    private String customer2Token;

    private User providerUser1;
    private User providerUser2;
    private User providerUser3;
    private ProviderProfile providerProfile1;
    private ProviderProfile providerProfile2;
    private ProviderProfile providerProfile3;
    private String provider1Token;
    private String provider2Token;
    private String provider3Token;

    private Role customerRole;
    private Role providerRole;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });

        customer1 = createTestUser("Nguyễn Khách 1", "cust1_" + UUID.randomUUID() + "@motofix.vn", customerRole, "CUSTOMER");
        customer1Token = jwtService.generateToken(customer1);

        customer2 = createTestUser("Trần Khách 2", "cust2_" + UUID.randomUUID() + "@motofix.vn", customerRole, "CUSTOMER");
        customer2Token = jwtService.generateToken(customer2);

        // Provider 1: Tại Q1 (10.7769, 106.7009), bán kính 5 km
        providerUser1 = createTestUser("Thợ Q1", "prov1_" + UUID.randomUUID() + "@motofix.vn", providerRole, "PROVIDER");
        provider1Token = jwtService.generateToken(providerUser1);
        providerProfile1 = createProvider(providerUser1, "Tiệm Sửa Xe Q1", 10.7769, 106.7009, 5.0f);

        // Provider 2: Tại Bình Thạnh (10.8000, 106.7100), bán kính 10 km
        providerUser2 = createTestUser("Thợ Bình Thạnh", "prov2_" + UUID.randomUUID() + "@motofix.vn", providerRole, "PROVIDER");
        provider2Token = jwtService.generateToken(providerUser2);
        providerProfile2 = createProvider(providerUser2, "Tiệm Sửa Xe Bình Thạnh", 10.8000, 106.7100, 10.0f);

        // Provider 3: Tại Thủ Đức (10.8500, 106.7500), bán kính 15 km
        providerUser3 = createTestUser("Thợ Thủ Đức", "prov3_" + UUID.randomUUID() + "@motofix.vn", providerRole, "PROVIDER");
        provider3Token = jwtService.generateToken(providerUser3);
        providerProfile3 = createProvider(providerUser3, "Tiệm Sửa Xe Thủ Đức", 10.8500, 106.7500, 15.0f);
    }

    private User createTestUser(String fullName, String email, Role role, String currentRole) {
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhone("09" + (System.currentTimeMillis() % 100000000));
        u.setPasswordHash(passwordEncoder.encode("Password123@"));
        u.setStatus("active");
        u.setCurrentRole(currentRole);
        u.getRoles().add(role);
        return userRepository.save(u);
    }

    private ProviderProfile createProvider(User user, String name, double lat, double lng, float radiusKm) {
        ProviderProfile p = new ProviderProfile();
        p.setUser(user);
        p.setBusinessName(name);
        p.setIdDocumentUrl("https://storage.motofix.vn/cccd.jpg");
        p.setAddress("Cơ sở " + name);
        p.setBaseLat((float) lat);
        p.setBaseLng((float) lng);
        p.setServiceRadiusKm(radiusKm);
        p.setApprovalStatus(ApprovalStatus.APPROVED);
        p.setActive(true);
        return providerProfileRepository.save(p);
    }

    private Booking createSearchingBooking(User customer, double lat, double lng) {
        Booking b = new Booking();
        b.setCustomer(customer);
        b.setStatus(BookingStatus.SEARCHING);
        b.setProblemDescription("Xe chết máy giữa đường");
        b.setAddress("Điểm cứu hộ quận 1");
        b.setVehicleBrand("Honda");
        b.setVehicleModel("Air Blade");
        b.setVehicleLicensePlate("59B1-123.45");
        b.setLatitude(lat);
        b.setLongitude(lng);
        b.setSearchRound(1);
        return bookingRepository.save(b);
    }

    @Test
    @DisplayName("BE04-01 (05-04, 05-19): Thợ đồng ý offer validate giá dương, max >= min, ETA nguyên dương")
    void testAcceptOffer_Validation_PricesAndEta() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer offer = new BookingOffer();
        offer.setBooking(b);
        offer.setProvider(providerProfile1);
        offer.setStatus(OfferStatus.PENDING);
        offer.setDistanceM(500.0);
        offer.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer = bookingOfferRepository.save(offer);

        UUID offerId = offer.getId();

        // 1. Giá min = 0 bị từ chối 400 Bad Request
        AcceptOfferRequest reqMinZero = new AcceptOfferRequest(
                BigDecimal.ZERO, BigDecimal.valueOf(200000), 15, "Sửa bình"
        );
        mockMvc.perform(post("/api/bookings/" + offerId + "/offer/accept")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqMinZero)))
                .andExpect(status().isBadRequest());

        // 2. Giá max < min bị từ chối 400 Bad Request
        AcceptOfferRequest reqMaxLessThanMin = new AcceptOfferRequest(
                BigDecimal.valueOf(250000), BigDecimal.valueOf(150000), 15, "Sửa bình"
        );
        mockMvc.perform(post("/api/bookings/" + offerId + "/offer/accept")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqMaxLessThanMin)))
                .andExpect(status().isBadRequest());

        // 3. ETA <= 0 bị từ chối 400 Bad Request
        AcceptOfferRequest reqEtaZero = new AcceptOfferRequest(
                BigDecimal.valueOf(150000), BigDecimal.valueOf(200000), 0, "Sửa bình"
        );
        mockMvc.perform(post("/api/bookings/" + offerId + "/offer/accept")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEtaZero)))
                .andExpect(status().isBadRequest());

        // 4. Hợp lệ (min = 150000, max = 200000, ETA = 15) -> Thành công 200 OK, offer đổi sang ACCEPTED
        AcceptOfferRequest validReq = new AcceptOfferRequest(
                BigDecimal.valueOf(150000), BigDecimal.valueOf(200000), 15, "Sửa bình ắc quy"
        );
        mockMvc.perform(post("/api/bookings/" + offerId + "/offer/accept")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.estimatedMin").value(150000))
                .andExpect(jsonPath("$.estimatedMax").value(200000))
                .andExpect(jsonPath("$.estimatedArrivalMinutes").value(15));

        BookingOffer updated = bookingOfferRepository.findById(offerId).orElseThrow();
        assertEquals(OfferStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    @DisplayName("BE04-02 (05-05): Phản hồi muộn sau expires_at bị từ chối 400 Bad Request")
    void testAcceptOffer_AfterExpiresAt_Rejected() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer offer = new BookingOffer();
        offer.setBooking(b);
        offer.setProvider(providerProfile1);
        offer.setStatus(OfferStatus.PENDING);
        offer.setDistanceM(500.0);
        // Hết hạn từ 5 phút trước
        offer.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        offer = bookingOfferRepository.save(offer);

        AcceptOfferRequest req = new AcceptOfferRequest(
                BigDecimal.valueOf(150000), BigDecimal.valueOf(200000), 15, "Đến ngay"
        );

        mockMvc.perform(post("/api/bookings/" + offer.getId() + "/offer/accept")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("BE04-03 (05-12): Khách vẫn chọn được offer ACCEPTED sau expires_at nếu booking còn SEARCHING")
    void testSelectOffer_AcceptedOffer_StillSelectableAfterExpiresAt() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer offer = new BookingOffer();
        offer.setBooking(b);
        offer.setProvider(providerProfile1);
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setEstimatedMin(BigDecimal.valueOf(150000));
        offer.setEstimatedMax(BigDecimal.valueOf(200000));
        offer.setEstimatedArrivalMinutes(15);
        offer.setDistanceM(500.0);
        // expires_at của PENDING ban đầu đã qua nhưng thợ đã ACCEPTED đúng hạn
        offer.setExpiresAt(LocalDateTime.now().minusMinutes(2));
        offer = bookingOfferRepository.save(offer);

        mockMvc.perform(post("/api/bookings/" + b.getId() + "/offers/" + offer.getId() + "/select")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED_PROVIDER"))
                .andExpect(jsonPath("$.providerId").value(providerProfile1.getId().toString()));

        Booking reloaded = bookingRepository.findById(b.getId()).orElseThrow();
        assertEquals(BookingStatus.SELECTED_PROVIDER, reloaded.getStatus());
        assertEquals(providerProfile1.getId(), reloaded.getProvider().getId());
    }

    @Test
    @DisplayName("BE04-04 (05-07): Hai khách cùng chọn một thợ - khách đầu thành công, offer của thợ trên đơn kia chuyển EXPIRED")
    void testConcurrentSelectSameProvider_OnlyOneWins_OtherOffersExpired() throws Exception {
        // Khách 1 có Booking 1
        Booking b1 = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer o1 = new BookingOffer();
        o1.setBooking(b1);
        o1.setProvider(providerProfile1);
        o1.setStatus(OfferStatus.ACCEPTED);
        o1.setDistanceM(500.0);
        o1.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        o1 = bookingOfferRepository.save(o1);

        // Khách 2 có Booking 2
        Booking b2 = createSearchingBooking(customer2, 10.7780, 106.7020);
        BookingOffer o2 = new BookingOffer();
        o2.setBooking(b2);
        o2.setProvider(providerProfile1); // Cùng thợ 1
        o2.setStatus(OfferStatus.ACCEPTED);
        o2.setDistanceM(600.0);
        o2.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        o2 = bookingOfferRepository.save(o2);

        // Khách 1 chọn Thợ 1 -> Thành công
        mockMvc.perform(post("/api/bookings/" + b1.getId() + "/offers/" + o1.getId() + "/select")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED_PROVIDER"));

        // Kiểm tra o2 của Thợ 1 trên Booking 2 tự động chuyển sang EXPIRED
        BookingOffer o2Reloaded = bookingOfferRepository.findById(o2.getId()).orElseThrow();
        assertEquals(OfferStatus.EXPIRED, o2Reloaded.getStatus());

        // Khách 2 cố tình chọn Thợ 1 -> Bị từ chối 409 Conflict ("Thợ vừa nhận đơn khác")
        mockMvc.perform(post("/api/bookings/" + b2.getId() + "/offers/" + o2.getId() + "/select")
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Thợ vừa nhận đơn khác")));

        // Booking 2 vẫn giữ nguyên trạng thái SEARCHING để khách tìm/chọn thợ khác
        Booking b2Reloaded = bookingRepository.findById(b2.getId()).orElseThrow();
        assertEquals(BookingStatus.SEARCHING, b2Reloaded.getStatus());
    }

    @Test
    @DisplayName("BE04-05 (05-08): Chọn thợ vs Thợ tắt online / đăng xuất")
    void testSelectOffer_WhenProviderTurnedOffline_FailsWithConflict() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer offer = new BookingOffer();
        offer.setBooking(b);
        offer.setProvider(providerProfile1);
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setDistanceM(500.0);
        offer.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer = bookingOfferRepository.save(offer);

        // Thợ tắt nhận việc (offline)
        providerProfile1.setActive(false);
        providerProfileRepository.save(providerProfile1);

        // Khách chọn thợ bị từ chối 409 Conflict
        mockMvc.perform(post("/api/bookings/" + b.getId() + "/offers/" + offer.getId() + "/select")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("tắt nhận việc")));
    }

    @Test
    @DisplayName("BE04-06 (05-10, 05-11, 05-16, 05-17): Tìm lại đa lượt 5 km -> 10 km -> 15 km")
    void testSearchRounds_ProgressionAndLimits() throws Exception {
        // Đặt điểm cứu hộ ở xa (Thủ Đức: 10.8500, 106.7500)
        // Provider 1 ở Q1 (cách ~11 km) không nằm trong 5 km hay 10 km
        // Provider 2 ở Bình Thạnh (cách ~7 km) nằm trong 10 km
        // Provider 3 ở Thủ Đức (cách ~1 km)
        Booking b = createSearchingBooking(customer1, 10.8500, 106.7500);

        // 1. Khi còn PENDING chưa tới hạn -> Bị chặn 409 Conflict không cho mở lượt mới (05-11)
        BookingOffer pendingOffer = new BookingOffer();
        pendingOffer.setBooking(b);
        pendingOffer.setProvider(providerProfile3);
        pendingOffer.setStatus(OfferStatus.PENDING);
        pendingOffer.setDistanceM(1000.0);
        pendingOffer.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        bookingOfferRepository.save(pendingOffer);

        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("chờ hết hạn")));

        // Giả lập offer hết hạn (EXPIRED)
        pendingOffer.setStatus(OfferStatus.EXPIRED);
        bookingOfferRepository.save(pendingOffer);

        // 2. Chống gửi lặp lượt: client gửi expectedRound không khớp -> 400 Bad Request
        SearchRoundRequest wrongRoundReq = new SearchRoundRequest(99);
        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRoundReq)))
                .andExpect(status().isBadRequest());

        // 3. Kích hoạt lượt 2 (10 km) thành công (05-10)
        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.radiusKm").value(10.0));

        Booking bRound2 = bookingRepository.findById(b.getId()).orElseThrow();
        assertEquals(2, bRound2.getSearchRound());

        // 4. Kích hoạt tiếp lượt 3 (15 km)
        // Trước tiên cho các offer lượt 2 hết hạn
        bookingOfferRepository.expireAllOffersForBooking(b.getId(), OfferStatus.EXPIRED, List.of(OfferStatus.PENDING));

        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(3))
                .andExpect(jsonPath("$.radiusKm").value(15.0));

        Booking bRound3 = bookingRepository.findById(b.getId()).orElseThrow();
        assertEquals(3, bRound3.getSearchRound());

        // 5. Quá 15 km (trần tối đa) -> Bị từ chối 400 Bad Request (05-17)
        bookingOfferRepository.expireAllOffersForBooking(b.getId(), OfferStatus.EXPIRED, List.of(OfferStatus.PENDING));

        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("15 km")));
    }

    @Test
    @DisplayName("BE04-07 (05-13, 05-14): Thợ DECLINED bị loại khỏi các lượt sau; thợ EXPIRED được nhận offer mới nhưng ID cũ bị từ chối")
    void testSearchRound_DeclinedExcluded_ExpiredReinvited_OldIdRejected() throws Exception {
        // Sử dụng tọa độ cô lập để tránh bị các thợ cũ trong DB chiếm slot LIMIT
        double testLat = 10.2345;
        double testLng = 106.2345;

        User provUserA = createTestUser("Thợ Declined", "declined_" + UUID.randomUUID() + "@motofix.vn", providerRole, "PROVIDER");
        ProviderProfile provA = createProvider(provUserA, "Tiệm Declined", testLat, testLng, 5.0f);

        User provUserB = createTestUser("Thợ Expired", "expired_" + UUID.randomUUID() + "@motofix.vn", providerRole, "PROVIDER");
        String provUserBToken = jwtService.generateToken(provUserB);
        ProviderProfile provB = createProvider(provUserB, "Tiệm Expired", testLat + 0.01, testLng + 0.01, 10.0f);

        Booking b = createSearchingBooking(customer1, testLat, testLng);

        // Thợ A từ chối (DECLINED)
        BookingOffer offer1 = new BookingOffer();
        offer1.setBooking(b);
        offer1.setProvider(provA);
        offer1.setStatus(OfferStatus.DECLINED);
        offer1.setDistanceM(50.0);
        offer1.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        bookingOfferRepository.save(offer1);

        // Thợ B hết hạn (EXPIRED)
        BookingOffer offer2 = new BookingOffer();
        offer2.setBooking(b);
        offer2.setProvider(provB);
        offer2.setStatus(OfferStatus.EXPIRED);
        offer2.setDistanceM(1500.0);
        offer2.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        offer2 = bookingOfferRepository.save(offer2);

        // Kích hoạt lượt 2 (10 km)
        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2));

        // Kiểm tra: Thợ A (DECLINED) KHÔNG được phát offer mới
        List<BookingOffer> offersOfProviderA = bookingOfferRepository.findByBookingIdAndStatusWithProvider(b.getId(), OfferStatus.PENDING)
                .stream().filter(o -> o.getProvider().getId().equals(provA.getId())).toList();
        assertTrue(offersOfProviderA.isEmpty(), "Thợ đã DECLINED không được nhận offer ở các vòng sau");

        // Kiểm tra: Thợ B (từng EXPIRED) ĐÃ ĐƯỢC mời lại với offer mới
        List<BookingOffer> newOffersOfProviderB = bookingOfferRepository.findByBookingIdAndStatusWithProvider(b.getId(), OfferStatus.PENDING)
                .stream().filter(o -> o.getProvider().getId().equals(provB.getId())).toList();
        assertEquals(1, newOffersOfProviderB.size(), "Thợ từng EXPIRED được mời lại bằng offer mới");
        UUID newOfferId = newOffersOfProviderB.get(0).getId();
        assertNotEquals(offer2.getId(), newOfferId);

        // Thử phản hồi từ màn hình offer cũ của Thợ B -> Bị từ chối 400 Bad Request (05-14)
        AcceptOfferRequest req = new AcceptOfferRequest(
                BigDecimal.valueOf(150000), BigDecimal.valueOf(200000), 15, "Nhận lại"
        );
        mockMvc.perform(post("/api/bookings/" + offer2.getId() + "/offer/accept")
                        .header("Authorization", "Bearer " + provUserBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        // Phản hồi bằng ID offer mới -> Thành công 200 OK
        mockMvc.perform(post("/api/bookings/" + newOfferId + "/offer/accept")
                        .header("Authorization", "Bearer " + provUserBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("BE04-08 (05-18): Booking đã hủy không thể kích hoạt tìm lại")
    void testSearchRound_CancelledBooking_CannotTrigger() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        b.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(b);

        mockMvc.perform(post("/api/bookings/" + b.getId() + "/search-rounds")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BE04-09: Thợ xem danh sách incoming offers (gồm cả PENDING còn hạn và ACCEPTED chờ khách chọn)")
    void testGetIncomingOffers_IncludesPendingAndAccepted() throws Exception {
        Booking b1 = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer o1 = new BookingOffer();
        o1.setBooking(b1);
        o1.setProvider(providerProfile1);
        o1.setStatus(OfferStatus.PENDING);
        o1.setDistanceM(500.0);
        o1.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        bookingOfferRepository.save(o1);

        Booking b2 = createSearchingBooking(customer2, 10.7780, 106.7020);
        BookingOffer o2 = new BookingOffer();
        o2.setBooking(b2);
        o2.setProvider(providerProfile1);
        o2.setStatus(OfferStatus.ACCEPTED);
        o2.setEstimatedMin(BigDecimal.valueOf(150000));
        o2.setEstimatedMax(BigDecimal.valueOf(200000));
        o2.setEstimatedArrivalMinutes(15);
        o2.setDistanceM(600.0);
        o2.setExpiresAt(LocalDateTime.now().minusMinutes(2)); // ACCEPTED còn hiển thị dù expiresAt đã qua
        bookingOfferRepository.save(o2);

        mockMvc.perform(get("/api/providers/incoming-offers")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("BE04-10: Thợ xem chi tiết 1 incoming offer GET /api/providers/incoming-offers/{offerId}")
    void testGetOfferDetails_SuccessAndAccessControl() throws Exception {
        Booking b = createSearchingBooking(customer1, 10.7769, 106.7009);
        BookingOffer offer = new BookingOffer();
        offer.setBooking(b);
        offer.setProvider(providerProfile1);
        offer.setStatus(OfferStatus.PENDING);
        offer.setDistanceM(500.0);
        offer.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        offer = bookingOfferRepository.save(offer);

        UUID offerId = offer.getId();

        // 1. Thợ sở hữu offer xem: Thành công 200 OK với đầy đủ thông tin xe, địa chỉ
        mockMvc.perform(get("/api/providers/incoming-offers/" + offerId)
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerId").value(offerId.toString()))
                .andExpect(jsonPath("$.bookingId").value(b.getId().toString()))
                .andExpect(jsonPath("$.vehicleBrand").value("Honda"))
                .andExpect(jsonPath("$.vehicleModel").value("Air Blade"))
                .andExpect(jsonPath("$.address").value("Điểm cứu hộ quận 1"))
                .andExpect(jsonPath("$.distanceM").value(500.0));

        // 2. Thợ khác xem: Bị từ chối 404 Not Found (không thấy offer của thợ khác)
        mockMvc.perform(get("/api/providers/incoming-offers/" + offerId)
                        .header("Authorization", "Bearer " + provider2Token))
                .andExpect(status().isNotFound());
    }
}
