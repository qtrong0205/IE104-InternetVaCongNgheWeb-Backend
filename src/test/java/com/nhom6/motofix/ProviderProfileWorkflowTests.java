package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.ProviderActiveRequest;
import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ProviderProfileWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProviderService providerService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User providerUser;
    private ProviderProfile providerProfile;
    private String providerToken;

    private User plainCustomer;
    private String customerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        // Tạo Provider User
        providerUser = new User();
        providerUser.setFullName("Thợ Sửa Xe Test");
        providerUser.setEmail("provider_" + UUID.randomUUID() + "@motofix.vn");
        providerUser.setPhone("098" + (System.currentTimeMillis() % 10000000));
        providerUser.setPasswordHash(passwordEncoder.encode("Password123@"));
        providerUser.setStatus("active");
        providerUser.getRoles().add(providerRole);
        providerUser = userRepository.save(providerUser);
        providerToken = jwtService.generateToken(providerUser);

        // Tạo Provider Profile ban đầu (APPROVED, offline)
        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Tiệm Sửa Xe Phát Đạt");
        providerProfile.setIdDocumentUrl("https://supabase.co/storage/id_doc_1.jpg");
        providerProfile.setBusinessLicenseUrl("https://supabase.co/storage/license_1.jpg");
        providerProfile.setBaseLat(10.7769f);
        providerProfile.setBaseLng(106.7009f);
        providerProfile.setServiceRadiusKm(5.0f);
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(false);
        providerProfile.setRatingAvg(4.8f);
        providerProfile.setRatingCount(12);
        providerProfile = providerProfileRepository.save(providerProfile);

        // Tạo Plain Customer (chưa có ProviderProfile)
        plainCustomer = new User();
        plainCustomer.setFullName("Khách Hàng Thường");
        plainCustomer.setEmail("customer_" + UUID.randomUUID() + "@motofix.vn");
        plainCustomer.setPhone("097" + ((System.currentTimeMillis() + 999) % 10000000));
        plainCustomer.setPasswordHash(passwordEncoder.encode("Password123@"));
        plainCustomer.setStatus("active");
        plainCustomer.getRoles().add(customerRole);
        plainCustomer = userRepository.save(plainCustomer);
        customerToken = jwtService.generateToken(plainCustomer);
    }

    // =========================================================================
    // 1. GET /api/providers/me
    // =========================================================================

    @Test
    @DisplayName("GET /api/providers/me: Provider APPROVED trả về 200 và đúng thông tin")
    void testGetMyProfile_ApprovedProvider_Success() throws Exception {
        mockMvc.perform(get("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(providerProfile.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Tiệm Sửa Xe Phát Đạt"))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.baseLat").value(10.7769))
                .andExpect(jsonPath("$.baseLng").value(106.7009));
    }

    @Test
    @DisplayName("GET /api/providers/me: Provider PENDING trả về 200")
    void testGetMyProfile_PendingProvider_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.PENDING);
        providerProfileRepository.save(providerProfile);

        mockMvc.perform(get("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("GET /api/providers/me: Provider REJECTED trả về 200 kèm rejectionReason")
    void testGetMyProfile_RejectedProvider_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.REJECTED);
        providerProfile.setRejectionReason("Ảnh CCCD bị mờ, vui lòng chụp lại rõ nét");
        providerProfileRepository.save(providerProfile);

        mockMvc.perform(get("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ảnh CCCD bị mờ, vui lòng chụp lại rõ nét"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("GET /api/providers/me: Người dùng chưa đăng ký Provider trả về 404")
    void testGetMyProfile_UserWithoutProviderProfile_NotFound() throws Exception {
        mockMvc.perform(get("/api/providers/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/providers/me: Chưa đăng nhập trả về 401 Unauthorized / 4xx")
    void testGetMyProfile_Unauthenticated_Unauthorized() throws Exception {
        assertThrows(UnauthorizedException.class, () -> providerService.getMyProfile(null));

        mockMvc.perform(get("/api/providers/me"))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // 2. PUT /api/providers/me
    // =========================================================================

    @Test
    @DisplayName("PUT /api/providers/me: Cập nhật thành công khi PENDING, offline và không có booking")
    void testUpdateMyProfile_PendingOfflineNoBooking_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.PENDING);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Sửa Xe Tiến Lên",
                "https://supabase.co/storage/id_new.jpg",
                "https://supabase.co/storage/license_new.jpg",
                10.8231f,
                106.6297f,
                7.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Tiệm Sửa Xe Tiến Lên"))
                .andExpect(jsonPath("$.idDocumentUrl").value("https://supabase.co/storage/id_new.jpg"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(false));

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals("Tiệm Sửa Xe Tiến Lên", updated.getBusinessName());
        assertEquals(ApprovalStatus.PENDING, updated.getApprovalStatus());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Cập nhật thành công khi REJECTED, chuyển về PENDING và xóa rejectionReason")
    void testUpdateMyProfile_RejectedOfflineNoBooking_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.REJECTED);
        providerProfile.setRejectionReason("Cần bổ sung giấy phép");
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Sửa Xe Phát Đạt Đã Bổ Sung",
                "https://supabase.co/storage/id_clear.jpg",
                "https://supabase.co/storage/valid_license.jpg",
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.isActive").value(false));

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, updated.getApprovalStatus());
        assertNull(updated.getRejectionReason());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Cập nhật thành công khi APPROVED, chuyển về PENDING chờ duyệt lại")
    void testUpdateMyProfile_ApprovedOfflineNoBooking_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Sửa Xe 24/7",
                "https://supabase.co/storage/id_doc_1.jpg",
                null,
                10.7769f,
                106.7009f,
                10.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Tiệm Sửa Xe 24/7"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(false));

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, updated.getApprovalStatus());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Từ chối cập nhật khi Provider đang ONLINE (400 Bad Request)")
    void testUpdateMyProfile_WhenOnline_BadRequest() throws Exception {
        providerProfile.setActive(true);
        providerProfileRepository.save(providerProfile);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Mới",
                "https://supabase.co/storage/id_doc_1.jpg",
                null,
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Từ chối cập nhật khi Provider có booking đang xử lý dù đang offline (400 Bad Request)")
    void testUpdateMyProfile_WhenOffline_WithActiveBooking_BadRequest() throws Exception {
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        // Tạo 1 booking gán cho provider này ở trạng thái IN_PROGRESS
        Booking booking = new Booking();
        booking.setCustomer(plainCustomer);
        booking.setProvider(providerProfile);
        booking.setProblemDescription("Thủng lốp xe");
        booking.setLatitude(10.7769);
        booking.setLongitude(106.7009);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setCreatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Mới",
                "https://supabase.co/storage/id_doc_1.jpg",
                null,
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Từ chối cập nhật khi vừa ONLINE vừa có booking đang xử lý (400 Bad Request)")
    void testUpdateMyProfile_WhenOnline_AndWithActiveBooking_BadRequest() throws Exception {
        providerProfile.setActive(true);
        providerProfileRepository.save(providerProfile);

        Booking booking = new Booking();
        booking.setCustomer(plainCustomer);
        booking.setProvider(providerProfile);
        booking.setProblemDescription("Chết bugi");
        booking.setLatitude(10.7769);
        booking.setLongitude(106.7009);
        booking.setStatus(BookingStatus.INSPECTING);
        booking.setCreatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Mới",
                "https://supabase.co/storage/id_doc_1.jpg",
                null,
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Cho phép cập nhật khi booking của provider đã COMPLETED hoặc CANCELLED")
    void testUpdateMyProfile_WhenBookingsCompletedOrCancelled_Success() throws Exception {
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        Booking completedBooking = new Booking();
        completedBooking.setCustomer(plainCustomer);
        completedBooking.setProvider(providerProfile);
        completedBooking.setProblemDescription("Thay nhớt đã xong");
        completedBooking.setLatitude(10.7769);
        completedBooking.setLongitude(106.7009);
        completedBooking.setStatus(BookingStatus.COMPLETED);
        completedBooking.setCreatedAt(LocalDateTime.now().minusDays(1));
        bookingRepository.save(completedBooking);

        Booking cancelledBooking = new Booking();
        cancelledBooking.setCustomer(plainCustomer);
        cancelledBooking.setProvider(providerProfile);
        cancelledBooking.setProblemDescription("Khách đổi ý hủy đơn");
        cancelledBooking.setLatitude(10.7769);
        cancelledBooking.setLongitude(106.7009);
        cancelledBooking.setStatus(BookingStatus.CANCELLED);
        cancelledBooking.setCreatedAt(LocalDateTime.now().minusHours(2));
        bookingRepository.save(cancelledBooking);

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Phát Đạt Hoàn Tất Đơn Cũ",
                "https://supabase.co/storage/id_doc_1.jpg",
                null,
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Tiệm Phát Đạt Hoàn Tất Đơn Cũ"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /api/providers/me: Ghi đè trực tiếp trên bản ghi hiện tại, không tạo bản ghi mới (ID không đổi)")
    void testUpdateMyProfile_OverwritesExistingRecord_NoDuplicateCreated() throws Exception {
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        long countBefore = providerProfileRepository.count();
        UUID originalId = providerProfile.getId();

        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Ghi Đè Bản Ghi",
                "https://supabase.co/storage/id_new.jpg",
                null,
                10.7769f,
                106.7009f,
                5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId.toString()));

        long countAfter = providerProfileRepository.count();
        assertEquals(countBefore, countAfter, "Số lượng bản ghi ProviderProfile không được tăng thêm");
    }

    @Test
    @DisplayName("PUT /api/providers/me: Thiếu trường bắt buộc trả về 400 Bad Request")
    void testUpdateMyProfile_MissingRequiredFields_BadRequest() throws Exception {
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        // Thiếu idDocumentUrl và baseLat, baseLng
        String invalidJson = """
                {
                    "business_name": "Tiệm Thiếu Trường",
                    "id_document_url": ""
                }
                """;

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Chưa đăng nhập trả về 401 Unauthorized / 4xx")
    void testUpdateMyProfile_Unauthenticated_Unauthorized() throws Exception {
        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Test", "https://supabase.co/id.jpg", null, 10.7f, 106.7f, 5.0f
        );

        assertThrows(UnauthorizedException.class, () -> providerService.updateMyProfile(null, request));

        mockMvc.perform(put("/api/providers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("PUT /api/providers/me: Người dùng không có ProviderProfile trả về 404 Not Found")
    void testUpdateMyProfile_UserWithoutProviderProfile_NotFound() throws Exception {
        ProviderProfileRequest request = new ProviderProfileRequest(
                "Tiệm Test", "https://supabase.co/id.jpg", null, 10.7f, 106.7f, 5.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 3. PATCH /api/providers/me/active
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/providers/me/active: Provider APPROVED bật online thành công")
    void testUpdateActiveStatus_ApprovedProvider_TurnOn_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderActiveRequest request = new ProviderActiveRequest(true);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertTrue(updated.isActive());
    }

    @Test
    @DisplayName("PATCH /api/providers/me/active: Provider APPROVED tắt online thành công")
    void testUpdateActiveStatus_ApprovedProvider_TurnOff_Success() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfileRepository.save(providerProfile);

        ProviderActiveRequest request = new ProviderActiveRequest(false);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        ProviderProfile updated = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("PATCH /api/providers/me/active: Provider PENDING không thể bật online (400 Bad Request)")
    void testUpdateActiveStatus_PendingProvider_CannotTurnOn_BadRequest() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.PENDING);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderActiveRequest request = new ProviderActiveRequest(true);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        ProviderProfile current = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertFalse(current.isActive(), "Trạng thái nhận đơn phải vẫn là false");
    }

    @Test
    @DisplayName("PATCH /api/providers/me/active: Provider REJECTED không thể bật online (400 Bad Request)")
    void testUpdateActiveStatus_RejectedProvider_CannotTurnOn_BadRequest() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.REJECTED);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        ProviderActiveRequest request = new ProviderActiveRequest(true);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        ProviderProfile current = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertFalse(current.isActive());
    }

    @Test
    @DisplayName("PATCH /api/providers/me/active: Người dùng chưa có ProviderProfile trả về 404")
    void testUpdateActiveStatus_UserWithoutProviderProfile_NotFound() throws Exception {
        ProviderActiveRequest request = new ProviderActiveRequest(true);

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/providers/me/active: Không cho phép thay đổi approvalStatus qua request này")
    void testUpdateActiveStatus_CannotChangeApprovalStatusViaApi() throws Exception {
        providerProfile.setApprovalStatus(ApprovalStatus.PENDING);
        providerProfile.setActive(false);
        providerProfileRepository.save(providerProfile);

        // Cố tình gửi body chứa approvalStatus = APPROVED
        String maliciousPayload = """
                {
                    "isActive": true,
                    "approvalStatus": "APPROVED",
                    "approval_status": "APPROVED"
                }
                """;

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isBadRequest());

        ProviderProfile current = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, current.getApprovalStatus(), "approvalStatus không được bị can thiệp");
    }

    // =========================================================================
    // 4. KIỂM THỬ TÍCH HỢP TOÀN BỘ LUỒNG NGHIỆP VỤ (End-to-End Workflow)
    // =========================================================================

    @Test
    @DisplayName("Kiểm thử tích hợp luồng nghiệp vụ 6 bước hoàn chỉnh")
    void testIntegratedBusinessFlow_FullLifecycle() throws Exception {
        // Bước 1: Provider đã duyệt (APPROVED) và đang online (is_active = true) -> không thể sửa hồ sơ
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfileRepository.save(providerProfile);

        ProviderProfileRequest editReq = new ProviderProfileRequest(
                "Tiệm Sau Khi Sửa Đổi",
                "https://supabase.co/new_id.jpg",
                null,
                10.7769f,
                106.7009f,
                8.0f
        );

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isBadRequest());

        // Bước 2: Provider tắt online (is_active = false) nhưng vẫn có booking đang xử lý -> không thể sửa hồ sơ
        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProviderActiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        Booking activeBooking = new Booking();
        activeBooking.setCustomer(plainCustomer);
        activeBooking.setProvider(providerProfile);
        activeBooking.setProblemDescription("Xe chết máy đang trên đường tới");
        activeBooking.setLatitude(10.7769);
        activeBooking.setLongitude(106.7009);
        activeBooking.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        activeBooking.setCreatedAt(LocalDateTime.now());
        activeBooking = bookingRepository.save(activeBooking);

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isBadRequest());

        // Bước 3: Đơn hoàn thành (COMPLETED), provider tắt online và không còn booking xử lý -> được phép sửa hồ sơ
        activeBooking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(activeBooking);

        mockMvc.perform(put("/api/providers/me")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Tiệm Sau Khi Sửa Đổi"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.isActive").value(false));

        // Bước 4: Sau khi sửa thành công -> trạng thái chuyển PENDING, is_active = false
        ProviderProfile refreshed = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, refreshed.getApprovalStatus());
        assertFalse(refreshed.isActive());

        // Bước 5: Provider đang PENDING -> không thể bật online
        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProviderActiveRequest(true))))
                .andExpect(status().isBadRequest());

        // Bước 6: Sau khi Admin duyệt lại (approveProvider) -> Provider có thể bật online và nhận booking mới
        providerService.approveProvider(providerProfile.getId());

        ProviderProfile approvedAgain = providerProfileRepository.findById(providerProfile.getId()).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, approvedAgain.getApprovalStatus());
        assertTrue(approvedAgain.isActive());

        // Có thể tắt/bật lại nhận đơn
        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProviderActiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProviderActiveRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }
}

