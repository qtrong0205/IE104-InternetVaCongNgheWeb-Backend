package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.PaymentMethod;
import com.nhom6.motofix.enums.QuoteStatus;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class BookingHistoryAndRevenueWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RepairQuoteRepository quoteRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    private ProviderProfile providerProfile1;
    private ProviderProfile providerProfile2;
    private String provider1Token;
    private String provider2Token;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        today = LocalDate.now();

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

        // Tạo Customer 1
        customer1 = new User();
        customer1.setFullName("Nguyễn Văn Khách 1");
        customer1.setEmail("customer1_" + UUID.randomUUID() + "@motofix.vn");
        customer1.setPhone("090" + (System.currentTimeMillis() % 10000000));
        customer1.setPasswordHash(passwordEncoder.encode("Password123@"));
        customer1.setStatus("active");
        customer1.getRoles().add(customerRole);
        customer1 = userRepository.save(customer1);
        customer1Token = jwtService.generateToken(customer1);

        // Tạo Customer 2
        customer2 = new User();
        customer2.setFullName("Trần Thị Khách 2");
        customer2.setEmail("customer2_" + UUID.randomUUID() + "@motofix.vn");
        customer2.setPhone("091" + ((System.currentTimeMillis() + 111) % 10000000));
        customer2.setPasswordHash(passwordEncoder.encode("Password123@"));
        customer2.setStatus("active");
        customer2.getRoles().add(customerRole);
        customer2 = userRepository.save(customer2);
        customer2Token = jwtService.generateToken(customer2);

        // Tạo Provider 1
        providerUser1 = new User();
        providerUser1.setFullName("Thợ Sửa Xe 1");
        providerUser1.setEmail("provider1_" + UUID.randomUUID() + "@motofix.vn");
        providerUser1.setPhone("098" + ((System.currentTimeMillis() + 222) % 10000000));
        providerUser1.setPasswordHash(passwordEncoder.encode("Password123@"));
        providerUser1.setStatus("active");
        providerUser1.getRoles().add(providerRole);
        providerUser1 = userRepository.save(providerUser1);
        provider1Token = jwtService.generateToken(providerUser1);

        providerProfile1 = new ProviderProfile();
        providerProfile1.setUser(providerUser1);
        providerProfile1.setBusinessName("Tiệm Phát Đạt 1");
        providerProfile1.setIdDocumentUrl("https://supabase.co/storage/doc1.jpg");
        providerProfile1.setBaseLat(10.7769f);
        providerProfile1.setBaseLng(106.7009f);
        providerProfile1.setServiceRadiusKm(5.0f);
        providerProfile1.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile1.setActive(true);
        providerProfile1 = providerProfileRepository.save(providerProfile1);

        // Tạo Provider 2
        providerUser2 = new User();
        providerUser2.setFullName("Thợ Sửa Xe 2");
        providerUser2.setEmail("provider2_" + UUID.randomUUID() + "@motofix.vn");
        providerUser2.setPhone("097" + ((System.currentTimeMillis() + 333) % 10000000));
        providerUser2.setPasswordHash(passwordEncoder.encode("Password123@"));
        providerUser2.setStatus("active");
        providerUser2.getRoles().add(providerRole);
        providerUser2 = userRepository.save(providerUser2);
        provider2Token = jwtService.generateToken(providerUser2);

        providerProfile2 = new ProviderProfile();
        providerProfile2.setUser(providerUser2);
        providerProfile2.setBusinessName("Tiệm Thành Công 2");
        providerProfile2.setIdDocumentUrl("https://supabase.co/storage/doc2.jpg");
        providerProfile2.setBaseLat(10.8231f);
        providerProfile2.setBaseLng(106.6297f);
        providerProfile2.setServiceRadiusKm(5.0f);
        providerProfile2.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile2.setActive(true);
        providerProfile2 = providerProfileRepository.save(providerProfile2);
    }

    private Booking createBooking(User customer, ProviderProfile provider, BookingStatus status,
                                  LocalDateTime createdAt, LocalDateTime updatedAt, BigDecimal amount, PaymentMethod method) {
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(provider);
        booking.setProblemDescription("Sự cố kiểm thử: " + UUID.randomUUID());
        booking.setLatitude(10.7769);
        booking.setLongitude(106.7009);
        booking.setStatus(status);
        booking.setPaymentMethod(method);
        booking.setCreatedAt(createdAt);
        booking.setUpdatedAt(updatedAt);
        Booking saved = bookingRepository.save(booking);

        if (amount != null) {
            RepairQuote quote = new RepairQuote();
            quote.setBooking(saved);
            quote.setVersion(1);
            quote.setDiagnosisNote("Chẩn đoán báo giá");
            quote.setTotalAmount(amount);
            quote.setStatus(QuoteStatus.ACCEPTED);
            quote.setCreatedAt(createdAt);
            quote.setRespondedAt(createdAt.plusMinutes(10));
            quoteRepository.save(quote);
            saved.getQuotes().add(quote);
        }

        return saved;
    }

    // =========================================================================
    // 1. GET /api/providers/bookings (Lịch sử đơn của Provider)
    // =========================================================================

    @Test
    @DisplayName("GET /api/providers/bookings: Trả về các đơn kết thúc (COMPLETED và CANCELLED) của Provider")
    void testGetProviderBookings_ReturnsCompletedAndCancelledOnly() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        // Đơn 1: COMPLETED của Provider 1 (kèm quote 250,000 đ)
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(3), now.minusHours(2), new BigDecimal("250000.00"), PaymentMethod.CASH);

        // Đơn 2: CANCELLED của Provider 1
        Booking b2 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                now.minusHours(5), now.minusHours(4), null, null);

        // Đơn 3: IN_PROGRESS của Provider 1 (đang xử lý -> KHÔNG được trả về trong lịch sử đơn kết thúc)
        Booking b3 = createBooking(customer1, providerProfile1, BookingStatus.IN_PROGRESS,
                now.minusMinutes(30), now.minusMinutes(10), new BigDecimal("100000.00"), null);

        // Đơn 4: COMPLETED của Provider 2 (thuộc thợ khác -> KHÔNG được trả về cho Provider 1)
        Booking b4 = createBooking(customer1, providerProfile2, BookingStatus.COMPLETED,
                now.minusHours(1), now.minusMinutes(30), new BigDecimal("500000.00"), PaymentMethod.BANK_TRANSFER);

        mockMvc.perform(get("/api/providers/bookings")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(b1.getId().toString(), b2.getId().toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(b3.getId().toString()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(b4.getId().toString()))))
                // Kiểm tra totalAmount được ánh xạ đúng
                .andExpect(jsonPath("$.content[?(@.id == '" + b1.getId() + "')].totalAmount").value(hasItem(250000.00)));
    }

    @Test
    @DisplayName("GET /api/providers/bookings: Lọc theo status=COMPLETED")
    void testGetProviderBookings_FilterByCompleted() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(2), now.minusHours(1), new BigDecimal("300000.00"), PaymentMethod.CASH);
        Booking b2 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                now.minusHours(3), now.minusHours(2), null, null);

        mockMvc.perform(get("/api/providers/bookings")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(b1.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/providers/bookings: Lọc theo status=CANCELLED")
    void testGetProviderBookings_FilterByCancelled() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(2), now.minusHours(1), new BigDecimal("300000.00"), PaymentMethod.CASH);
        Booking b2 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                now.minusHours(3), now.minusHours(2), null, null);

        mockMvc.perform(get("/api/providers/bookings")
                        .param("status", "CANCELLED")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(b2.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/providers/bookings: Lọc theo status không phải kết thúc -> 400 Bad Request")
    void testGetProviderBookings_InvalidStatus_BadRequest() throws Exception {
        mockMvc.perform(get("/api/providers/bookings")
                        .param("status", "IN_PROGRESS")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/providers/bookings: Customer không có quyền gọi -> 403 Forbidden")
    void testGetProviderBookings_CustomerRole_Forbidden() throws Exception {
        mockMvc.perform(get("/api/providers/bookings")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. GET /api/providers/me/revenue (Doanh thu của Provider)
    // =========================================================================

    @Test
    @DisplayName("GET /api/providers/me/revenue: Tính đúng tổng doanh thu theo ngày chỉ định")
    void testGetMyRevenue_SpecifiedDate_CalculatesCorrectly() throws Exception {
        LocalDateTime todayMorning = today.atTime(8, 30);
        LocalDateTime todayAfternoon = today.atTime(15, 45);
        LocalDateTime yesterday = today.minusDays(1).atTime(14, 0);

        // Đơn 1: Hoàn thành hôm nay với quote 400,000 đ
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                todayMorning.minusHours(1), todayMorning, new BigDecimal("400000.00"), PaymentMethod.CASH);

        // Đơn 2: Hoàn thành hôm nay với quote 350,000 đ
        Booking b2 = createBooking(customer2, providerProfile1, BookingStatus.COMPLETED,
                todayAfternoon.minusHours(2), todayAfternoon, new BigDecimal("350000.00"), PaymentMethod.BANK_TRANSFER);

        // Đơn 3: CANCELLED hôm nay -> KHÔNG được tính vào doanh thu
        Booking b3 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                todayMorning, todayAfternoon, null, null);

        // Đơn 4: Hoàn thành HÔM QUA (500,000 đ) -> KHÔNG tính vào doanh thu hôm nay
        Booking b4 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                yesterday.minusHours(1), yesterday, new BigDecimal("500000.00"), PaymentMethod.CASH);

        // Đơn 5: Hoàn thành hôm nay của Provider 2 (600,000 đ) -> KHÔNG tính cho Provider 1
        Booking b5 = createBooking(customer1, providerProfile2, BookingStatus.COMPLETED,
                todayMorning, todayAfternoon, new BigDecimal("600000.00"), PaymentMethod.CASH);

        mockMvc.perform(get("/api/providers/me/revenue")
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.totalRevenue").value(750000.00))
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.bookings[*].id", containsInAnyOrder(b1.getId().toString(), b2.getId().toString())));
    }

    @Test
    @DisplayName("GET /api/providers/me/revenue: Không truyền tham số date -> mặc định là hôm nay")
    void testGetMyRevenue_DefaultDateToday() throws Exception {
        LocalDateTime todayNoon = today.atTime(12, 0);

        createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                todayNoon.minusHours(1), todayNoon, new BigDecimal("180000.00"), PaymentMethod.CASH);

        mockMvc.perform(get("/api/providers/me/revenue")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.totalRevenue").value(180000.00))
                .andExpect(jsonPath("$.totalBookings").value(1));
    }

    @Test
    @DisplayName("GET /api/providers/me/revenue: Ngày không có đơn nào hoàn tất -> trả về doanh thu 0")
    void testGetMyRevenue_EmptyRevenueDate() throws Exception {
        LocalDate emptyDate = today.minusDays(10);

        mockMvc.perform(get("/api/providers/me/revenue")
                        .param("date", emptyDate.toString())
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(emptyDate.toString()))
                .andExpect(jsonPath("$.totalRevenue").value(0))
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.bookings", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/providers/me/revenue: Customer không có quyền gọi -> 403 Forbidden")
    void testGetMyRevenue_CustomerForbidden() throws Exception {
        mockMvc.perform(get("/api/providers/me/revenue")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. GET /api/bookings (Lịch sử đơn của Customer)
    // =========================================================================

    @Test
    @DisplayName("GET /api/bookings: Khách hàng chỉ xem các đơn kết thúc của chính mình")
    void testGetCustomerBookings_ReturnsOwnCompletedAndCancelledOnly() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        // Đơn 1: Customer 1 - COMPLETED
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(4), now.minusHours(3), new BigDecimal("320000.00"), PaymentMethod.CASH);

        // Đơn 2: Customer 1 - CANCELLED
        Booking b2 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                now.minusHours(6), now.minusHours(5), null, null);

        // Đơn 3: Customer 1 - SEARCHING (đang tìm thợ -> KHÔNG được hiện trong lịch sử kết thúc)
        Booking b3 = createBooking(customer1, null, BookingStatus.SEARCHING,
                now.minusMinutes(20), now.minusMinutes(20), null, null);

        // Đơn 4: Customer 2 - COMPLETED (của khách khác -> KHÔNG được trả về cho Customer 1)
        Booking b4 = createBooking(customer2, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(2), now.minusHours(1), new BigDecimal("500000.00"), PaymentMethod.CASH);

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(b1.getId().toString(), b2.getId().toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(b3.getId().toString()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(b4.getId().toString()))))
                .andExpect(jsonPath("$.content[?(@.id == '" + b1.getId() + "')].totalAmount").value(hasItem(320000.00)));
    }

    @Test
    @DisplayName("GET /api/bookings: Customer lọc theo status=COMPLETED")
    void testGetCustomerBookings_FilterCompleted() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking b1 = createBooking(customer1, providerProfile1, BookingStatus.COMPLETED,
                now.minusHours(3), now.minusHours(2), new BigDecimal("200000.00"), PaymentMethod.CASH);
        Booking b2 = createBooking(customer1, providerProfile1, BookingStatus.CANCELLED,
                now.minusHours(5), now.minusHours(4), null, null);

        mockMvc.perform(get("/api/bookings")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(b1.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/bookings: Customer lọc theo status không phải kết thúc -> 400 Bad Request")
    void testGetCustomerBookings_InvalidStatus_BadRequest() throws Exception {
        mockMvc.perform(get("/api/bookings")
                        .param("status", "SEARCHING")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/bookings: Chưa đăng nhập -> 401/403")
    void testGetCustomerBookings_Unauthenticated_Forbidden() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().is4xxClientError());
    }
}

