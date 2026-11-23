package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.*;
import com.nhom6.motofix.dto.respond.*;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BE09 · Ca kiểm thử xuyên suốt toàn diện (End-to-End full lifecycle)
 * Phục vụ nghiệm thu toàn bộ các use cases nghiệp vụ từ đăng ký -> duyệt admin -> đặt đơn -> báo giá -> thanh toán -> đánh giá -> chat -> doanh thu.
 */
@SpringBootTest
public class BE09EndToEndWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingOfferRepository offerRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private String adminToken;

    private Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            return roleRepository.save(r);
        });
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Role adminRole = getOrCreateRole("ADMIN");

        adminUser = userRepository.findByEmail("admin_be09@motofix.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("System Admin BE09");
            u.setEmail("admin_be09@motofix.com");
            u.setPhone("099" + (System.currentTimeMillis() % 10000000));
            u.setPasswordHash(passwordEncoder.encode("AdminPass123@"));
            u.setStatus("active");
            u.setCurrentRole("ADMIN");
            u.getRoles().add(adminRole);
            return userRepository.save(u);
        });

        if (!adminUser.getRoles().contains(adminRole)) {
            adminUser.getRoles().add(adminRole);
            adminUser = userRepository.save(adminUser);
        }
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    @DisplayName("BE09-E2E-01: Ca xuyên suốt toàn bộ vòng đời (Khách K1 + Thợ T1 + Admin)")
    void testCompleteEndToEndLifecycle_CustomerProviderAndAdmin() throws Exception {
        // 1. Đăng ký & đăng nhập Khách hàng K1
        String k1Email = "k1_" + UUID.randomUUID() + "@test.com";
        String rawPassword = "Password123@";

        RegisterRequest k1Reg = new RegisterRequest(
                "Nguyen Van Khach 1",
                k1Email,
                "091" + (System.currentTimeMillis() % 10000000),
                rawPassword
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(k1Reg)))
                .andExpect(status().isCreated());

        LoginRequest k1Login = new LoginRequest(k1Email, rawPassword);
        MvcResult loginResultK1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(k1Login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String k1Token = objectMapper.readTree(loginResultK1.getResponse().getContentAsString()).get("accessToken").asText();
        User k1 = userRepository.findByEmail(k1Email).orElseThrow();

        // 2. K1 thêm xe cá nhân
        CreateVehicleRequest vehicleReq = new CreateVehicleRequest("Honda", "Air Blade 125", "59-X1 12345");
        mockMvc.perform(post("/api/users/me/vehicles")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehicleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("59-X1 12345"));

        // 3. Đăng ký tài khoản thợ T1
        String t1Email = "t1_" + UUID.randomUUID() + "@test.com";
        RegisterRequest t1Reg = new RegisterRequest(
                "Le Van Tho 1",
                t1Email,
                "098" + (System.currentTimeMillis() % 10000000),
                rawPassword
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1Reg)))
                .andExpect(status().isCreated());

        LoginRequest t1Login = new LoginRequest(t1Email, rawPassword);
        MvcResult loginResultT1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1Login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String t1Token = objectMapper.readTree(loginResultT1.getResponse().getContentAsString()).get("accessToken").asText();
        User t1 = userRepository.findByEmail(t1Email).orElseThrow();

        // 4. Thợ T1 nộp hồ sơ đăng ký cơ sở
        ProviderProfileRequest t1ProfileReq = new ProviderProfileRequest(
                "Tiem Sua Xe Tho 1",
                "https://supabase.co/cccd_t1.jpg",
                "https://supabase.co/gpkd_t1.jpg",
                "456 Hai Ba Trung, Quan 1, TP.HCM",
                10.7800f,
                106.6990f,
                10.0f
        );
        mockMvc.perform(post("/api/providers/register")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1ProfileReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(false));

        ProviderProfile t1Profile = providerProfileRepository.findByUserId(t1.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, t1Profile.getApprovalStatus());

        // 5. Admin kiểm tra danh sách pending và phê duyệt hồ sơ thợ T1
        mockMvc.perform(get("/api/admin/providers/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/providers/" + t1Profile.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.isActive").value(false)); // Giữ offline sau duyệt!

        // 6. Thợ T1 đăng nhập lại, chuyển vai sang PROVIDER và tự bật online
        t1 = userRepository.findById(t1.getId()).orElseThrow();
        t1Token = jwtService.generateToken(t1);

        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("PROVIDER"))))
                .andExpect(status().isOk());

        t1 = userRepository.findById(t1.getId()).orElseThrow();
        t1Token = jwtService.generateToken(t1);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProviderActiveRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));

        // 7. Khách hàng K1 tạo đơn cứu hộ
        CreateBookingRequest bookingReq = new CreateBookingRequest(
                "Xe bi thung xam va dut xich",
                List.of(new MediaRequest("https://supabase.co/xe_hong.jpg", com.nhom6.motofix.enums.MediaType.IMAGE)),
                10.7805,
                106.6995,
                "450 Hai Ba Trung, Quan 1, TP.HCM",
                "Honda",
                "Air Blade 125",
                "59-X1 12345"
        );

        MvcResult createBookingResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andReturn();

        UUID bookingId = UUID.fromString(objectMapper.readTree(createBookingResult.getResponse().getContentAsString()).get("id").asText());

        // 8. Thợ T1 nhận offer trong inbox và chấp nhận offer
        List<BookingOffer> offers = offerRepository.findByBookingId(bookingId);
        assertFalse(offers.isEmpty(), "Thợ T1 phải nhận được offer tự động trong bán kính");
        BookingOffer t1Offer = offers.stream()
                .filter(o -> o.getProvider().getId().equals(t1Profile.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(OfferStatus.PENDING, t1Offer.getStatus());

        AcceptOfferRequest acceptOfferReq = new AcceptOfferRequest(
                BigDecimal.valueOf(80000),
                BigDecimal.valueOf(200000),
                15,
                "Toi se den trong 15 phut"
        );

        mockMvc.perform(post("/api/bookings/" + t1Offer.getId() + "/offer/accept")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptOfferReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 9. Khách K1 xem danh sách offers và chọn thợ T1
        mockMvc.perform(get("/api/bookings/" + bookingId + "/offers")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + bookingId + "/offers/" + t1Offer.getId() + "/select")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED_PROVIDER"))
                .andExpect(jsonPath("$.providerId").value(t1Profile.getId().toString()));

        // 10. Thợ T1 xác nhận đang đến -> đến nơi
        mockMvc.perform(patch("/api/bookings/" + bookingId + "/en-route")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVIDER_EN_ROUTE"));

        mockMvc.perform(patch("/api/bookings/" + bookingId + "/arrived")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTING"));

        // 11. Chat giữa K1 và T1
        mockMvc.perform(post("/api/bookings/" + bookingId + "/messages")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendMessageRequest("Toi da dung truoc cong nha 450"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("PROVIDER"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/messages")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendMessageRequest("Vang, toi ra ngay day!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("CUSTOMER"));

        mockMvc.perform(get("/api/bookings/" + bookingId + "/messages?page=0&size=50")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // 12. Thợ T1 gửi báo giá v1
        CreateQuoteRequest quoteReq = new CreateQuoteRequest(
                "Kiem tra: Varo sam bi rach lon, xich bi dut mat noi",
                List.of(
                        new CreateQuoteItemRequest(QuoteItemType.LABOR, "Tien cong thay sam va noi xich", 1, BigDecimal.valueOf(80000), "Cong sua"),
                        new CreateQuoteItemRequest(QuoteItemType.PART, "Sam xe Honda chinh hang", 1, BigDecimal.valueOf(120000), "Phu tung")
                )
        );

        MvcResult quoteResult = mockMvc.perform(post("/api/bookings/" + bookingId + "/quotes")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(200000))
                .andReturn();

        UUID quoteId = UUID.fromString(objectMapper.readTree(quoteResult.getResponse().getContentAsString()).get("id").asText());

        // 13. Khách K1 chấp nhận báo giá v1 -> Đơn chuyển IN_PROGRESS
        mockMvc.perform(post("/api/bookings/" + bookingId + "/quotes/" + quoteId + "/accept")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Booking bInProgress = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.IN_PROGRESS, bInProgress.getStatus());

        // 14. Thợ T1 hoàn thành sửa xe -> WAITING_PAYMENT
        mockMvc.perform(patch("/api/bookings/" + bookingId + "/complete")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_PAYMENT"));

        // 15. Thợ T1 xác nhận nhận tiền mặt -> COMPLETED
        mockMvc.perform(patch("/api/bookings/" + bookingId + "/payment/confirm")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPaymentRequest(PaymentMethod.CASH))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.reviewDeadline").isNotEmpty());

        // 16. Khách K1 đánh giá 5 sao
        mockMvc.perform(post("/api/bookings/" + bookingId + "/review")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Tho den nhanh, sua nhiet tinh va can than"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        // 17. Thợ T1 xem doanh thu ngày -> Có doanh thu từ đơn 200,000 VND
        mockMvc.perform(get("/api/providers/me/revenue")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").isNumber());

        // 18. Lịch sử đơn hai bên
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/providers/bookings")
                        .header("Authorization", "Bearer " + t1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("BE09-E2E-02: Ma trận hủy đơn và dọn dẹp trạng thái offer/quote")
    void testEndToEnd_CancellationAndCleanupMatrix() throws Exception {
        Role customerRole = getOrCreateRole("CUSTOMER");
        Role providerRole = getOrCreateRole("PROVIDER");

        User k1 = new User();
        k1.setFullName("Khach Hang Huy");
        k1.setEmail("k_cancel_" + UUID.randomUUID() + "@test.com");
        k1.setPhone("092" + (System.currentTimeMillis() % 10000000));
        k1.setPasswordHash("hash");
        k1.setStatus("active");
        k1.setCurrentRole("CUSTOMER");
        k1.getRoles().add(customerRole);
        k1 = userRepository.save(k1);
        String k1Token = jwtService.generateToken(k1);

        User t1 = new User();
        t1.setFullName("Tho Huy");
        t1.setEmail("t_cancel_" + UUID.randomUUID() + "@test.com");
        t1.setPhone("094" + (System.currentTimeMillis() % 10000000));
        t1.setPasswordHash("hash");
        t1.setStatus("active");
        t1.setCurrentRole("PROVIDER");
        t1.getRoles().add(providerRole);
        t1 = userRepository.save(t1);
        String t1Token = jwtService.generateToken(t1);

        ProviderProfile p1 = new ProviderProfile();
        p1.setUser(t1);
        p1.setBusinessName("Tiem Tho Huy");
        p1.setAddress("789 Dien Bien Phu");
        p1.setBaseLat(10.7900f);
        p1.setBaseLng(106.7100f);
        p1.setServiceRadiusKm(10.0f);
        p1.setApprovalStatus(ApprovalStatus.APPROVED);
        p1.setActive(true);
        p1 = providerProfileRepository.save(p1);

        CreateBookingRequest bReq = new CreateBookingRequest(
                "Xe hu giua duong",
                List.of(),
                10.7905,
                106.7105,
                "790 Dien Bien Phu",
                "Yamaha",
                "Exciter",
                "59-Y1 99999"
        );

        BookingResponse b = bookingService.createBooking(bReq, k1.getId());

        // Đang SEARCHING: Thợ hủy bị 403 Forbidden
        mockMvc.perform(post("/api/bookings/" + b.id() + "/cancel")
                        .header("Authorization", "Bearer " + t1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelBookingRequest("Khong muon lam"))))
                .andExpect(status().isForbidden());

        // Khách hủy thành công
        mockMvc.perform(post("/api/bookings/" + b.id() + "/cancel")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelBookingRequest("Tim duoc xe nguoi quen"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Đơn đã hủy: Không ai được phép hủy tiếp (409 Conflict)
        mockMvc.perform(post("/api/bookings/" + b.id() + "/cancel")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelBookingRequest("Huy lai"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BE09-E2E-03: Chặn đăng xuất và chuyển vai khi có đơn active")
    void testEndToEnd_BlockedLogoutAndRoleSwitch_WhenActiveBooking() throws Exception {
        Role customerRole = getOrCreateRole("CUSTOMER");
        Role providerRole = getOrCreateRole("PROVIDER");

        User k1 = new User();
        k1.setFullName("Khach Active");
        k1.setEmail("k_act_" + UUID.randomUUID() + "@test.com");
        k1.setPhone("095" + (System.currentTimeMillis() % 10000000));
        k1.setPasswordHash("hash");
        k1.setStatus("active");
        k1.setCurrentRole("CUSTOMER");
        k1.getRoles().add(customerRole);
        k1.getRoles().add(providerRole); // Có cả vai thợ để đủ điều kiện switch role
        k1 = userRepository.save(k1);

        ProviderProfile p1 = new ProviderProfile();
        p1.setUser(k1);
        p1.setBusinessName("Tiem Cua Khach Kiem Tho");
        p1.setAddress("100 Tran Hung Dao");
        p1.setBaseLat(10.7700f);
        p1.setBaseLng(106.6900f);
        p1.setApprovalStatus(ApprovalStatus.APPROVED);
        p1.setActive(false);
        providerProfileRepository.save(p1);

        String k1Token = jwtService.generateToken(k1);

        CreateBookingRequest bReq = new CreateBookingRequest(
                "Xe chet may",
                List.of(),
                10.7700,
                106.6900,
                "100 Tran Hung Dao",
                "Honda",
                "Wave Alpha",
                "59-A1 11111"
        );
        BookingResponse b = bookingService.createBooking(bReq, k1.getId());
        assertEquals(BookingStatus.SEARCHING, b.status());

        // Khách có đơn SEARCHING -> cố tình gọi chuyển vai bị 409 Conflict
        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + k1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("PROVIDER"))))
                .andExpect(status().isConflict());

        // Khách có đơn SEARCHING -> cố tình đăng xuất bị 409 Conflict
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + k1Token))
                .andExpect(status().isConflict());
    }
}
