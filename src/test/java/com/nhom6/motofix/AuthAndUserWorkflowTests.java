package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.ForgotPasswordRequest;
import com.nhom6.motofix.dto.request.LoginRequest;
import com.nhom6.motofix.dto.request.ResetPasswordRequest;
import com.nhom6.motofix.dto.request.ResetPasswordWithTokenRequest;
import com.nhom6.motofix.dto.request.SwitchRoleRequest;
import com.nhom6.motofix.dto.request.UpdateUserProfileRequest;
import com.nhom6.motofix.dto.respond.LoginResponse;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.SwitchRoleResponse;
import com.nhom6.motofix.dto.respond.UserProfileResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.Gender;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.AuthService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.MockEmailService;
import com.nhom6.motofix.service.UserService;
import com.nhom6.motofix.controller.AuthController;
import com.nhom6.motofix.controller.UserController;
import com.nhom6.motofix.exception.GlobalExceptionHandler;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuthAndUserWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private AuthController authController;

    @Autowired
    private UserController userController;

    @Autowired
    private org.springframework.web.context.WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingOfferRepository bookingOfferRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private MockEmailService mockEmailService;

    @Autowired
    private BookingService bookingService;

    private User testUser;
    private User otherUser;
    private String rawPassword;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("USER");
            return roleRepository.save(r);
        });

        rawPassword = "OldPassword123@";

        testUser = new User();
        testUser.setFullName("Nguyen Van Test");
        testUser.setEmail("test_" + UUID.randomUUID() + "@motofix.vn");
        testUser.setPhone("090" + System.currentTimeMillis() % 10000000);
        testUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        testUser.setStatus("active");
        testUser.setAvatarUrl("https://example.com/avatar1.png");
        testUser.getRoles().add(userRole);
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateToken(testUser);

        otherUser = new User();
        otherUser.setFullName("Tran Thi Other");
        otherUser.setEmail("other_" + UUID.randomUUID() + "@motofix.vn");
        otherUser.setPhone("098" + (System.currentTimeMillis() + 123) % 10000000);
        otherUser.setPasswordHash(passwordEncoder.encode("OtherPass123@"));
        otherUser.setStatus("active");
        otherUser.getRoles().add(userRole);
        otherUser = userRepository.save(otherUser);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // =========================================================================
    // API 1: POST /api/auth/logout
    // =========================================================================

    @Test
    @DisplayName("API 1 - Logout: Service đăng xuất thành công")
    void testLogout_Service_Success() {
        MessageResponse response = authService.logout(testUser);
        assertNotNull(response);
        assertEquals("Logged out successfully", response.message());
    }

    @Test
    @DisplayName("API 1 - Logout: MockMvc HTTP POST /api/auth/logout trả về 200 OK")
    void testLogout_Http_Success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // =========================================================================
    // API 2: POST /api/auth/reset-password
    // =========================================================================

    @Test
    @DisplayName("API 2 - Reset Password: Đổi mật khẩu thành công và đăng nhập được bằng mật khẩu mới")
    void testResetPassword_Success() {
        String newPass = "NewStrongPassword456@";
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, newPass, newPass);

        MessageResponse response = authService.resetPassword(testUser, request);
        assertEquals("Password reset successfully", response.message());

        // Kiểm tra mật khẩu trong CSDL đã cập nhật
        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(newPass, reloaded.getPasswordHash()));
        assertFalse(passwordEncoder.matches(rawPassword, reloaded.getPasswordHash()));

        // Đăng nhập bằng mật khẩu mới thành công
        var loginResponse = authService.login(new LoginRequest(testUser.getEmail(), newPass));
        assertNotNull(loginResponse.accessToken());

        // Đăng nhập bằng mật khẩu cũ phải thất bại
        assertThrows(UnauthorizedException.class, () ->
                authService.login(new LoginRequest(testUser.getEmail(), rawPassword)));
    }

    @Test
    @DisplayName("API 2 - Reset Password: MockMvc HTTP POST /api/auth/reset-password thành công trả 200 OK")
    void testResetPassword_Http_Success() throws Exception {
        String newPass = "HttpNewPass789@";
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, newPass, newPass);

        mockMvc.perform(post("/api/auth/reset-password")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));
    }

    @Test
    @DisplayName("API 2 - Reset Password: Chưa đăng nhập -> 401 Unauthorized")
    void testResetPassword_Unauthenticated_ThrowsUnauthorized() {
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, "newPass123@", "newPass123@");
        assertThrows(UnauthorizedException.class, () ->
                authService.resetPassword(null, request));
    }

    @Test
    @DisplayName("API 2 - Reset Password: MockMvc HTTP không token -> 401 Unauthorized")
    void testResetPassword_Http_NoToken_Returns401() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, "newPass123@", "newPass123@");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API 2 - Reset Password: Mật khẩu hiện tại không đúng -> 400 Bad Request")
    void testResetPassword_WrongCurrentPassword_ThrowsBadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest("WrongCurrentPass", "newPass123@", "newPass123@");
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.resetPassword(testUser, request));
        assertEquals("Current password is incorrect", ex.getMessage());
    }

    @Test
    @DisplayName("API 2 - Reset Password: Mật khẩu mới và xác nhận không khớp -> 400 Bad Request")
    void testResetPassword_ConfirmPasswordMismatch_ThrowsBadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, "newPass123@", "differentConfirmPass@");
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.resetPassword(testUser, request));
        assertEquals("New password and confirmation password do not match", ex.getMessage());
    }

    @Test
    @DisplayName("API 2 - Reset Password: Mật khẩu mới trùng mật khẩu hiện tại -> 400 Bad Request")
    void testResetPassword_SameAsCurrentPassword_ThrowsBadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, rawPassword, rawPassword);
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.resetPassword(testUser, request));
        assertEquals("New password must not be the same as the current password", ex.getMessage());
    }

    @Test
    @DisplayName("API 2 - Reset Password: Tài khoản bị khóa (locked) -> 401 Unauthorized")
    void testResetPassword_AccountLocked_ThrowsUnauthorized() {
        testUser.setStatus("locked");
        userRepository.save(testUser);

        ResetPasswordRequest request = new ResetPasswordRequest(rawPassword, "newPass123@", "newPass123@");
        assertThrows(UnauthorizedException.class, () ->
                authService.resetPassword(testUser, request));
    }

    // =========================================================================
    // API 3: GET /api/users/me
    // =========================================================================

    @Test
    @DisplayName("API 3 - Get Profile: Lấy thông tin cá nhân thành công")
    void testGetProfile_Success() {
        UserProfileResponse profile = userService.getProfile(testUser);

        assertNotNull(profile);
        assertEquals(testUser.getId(), profile.id());
        assertEquals(testUser.getFullName(), profile.fullName());
        assertEquals(testUser.getEmail(), profile.email());
        assertEquals(testUser.getPhone(), profile.phone());
        assertEquals("https://example.com/avatar1.png", profile.avatarUrl());
        assertEquals("active", profile.status());
        assertTrue(profile.roles().contains("USER"));
    }

    @Test
    @DisplayName("API 3 - Get Profile: MockMvc HTTP GET /api/users/me trả về 200 OK kèm thông tin")
    void testGetProfile_Http_Success() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.fullName").value(testUser.getFullName()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.phone").value(testUser.getPhone()))
                .andExpect(jsonPath("$.avatarUrl").value(testUser.getAvatarUrl()))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @DisplayName("API 3 - Get Profile: Không đăng nhập -> 401 Unauthorized")
    void testGetProfile_Unauthenticated_ThrowsUnauthorized() {
        assertThrows(UnauthorizedException.class, () ->
                userService.getProfile(null));
    }

    @Test
    @DisplayName("API 3 - Get Profile: MockMvc HTTP không token -> 401 hoặc 403")
    void testGetProfile_Http_NoToken_ForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // API 4: PUT /api/users/me
    // =========================================================================

    @Test
    @DisplayName("API 4 - Update Profile: Cập nhật họ tên thành công")
    void testUpdateProfile_FullName_Success() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Nguyen Van Updated", null, null);

        UserProfileResponse response = userService.updateProfile(testUser, request);
        assertEquals("Nguyen Van Updated", response.fullName());

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Nguyen Van Updated", reloaded.getFullName());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Cập nhật số điện thoại thành công")
    void testUpdateProfile_Phone_Success() {
        String newPhone = "0977665544";
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(null, newPhone, null);

        UserProfileResponse response = userService.updateProfile(testUser, request);
        assertEquals(newPhone, response.phone());

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(newPhone, reloaded.getPhone());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Giữ nguyên số điện thoại của chính mình -> thành công")
    void testUpdateProfile_KeepSamePhone_Success() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Ten Moi", testUser.getPhone(), null);

        UserProfileResponse response = userService.updateProfile(testUser, request);
        assertEquals(testUser.getPhone(), response.phone());
        assertEquals("Ten Moi", response.fullName());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Đổi số điện thoại trùng người khác -> 400 Bad Request")
    void testUpdateProfile_DuplicatePhone_ThrowsBadRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(null, otherUser.getPhone(), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                userService.updateProfile(testUser, request));
        assertEquals("Phone already exists", ex.getMessage());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Cập nhật avatar URL thành công")
    void testUpdateProfile_AvatarUrl_Success() {
        String newAvatar = "https://example.com/new-avatar.jpg";
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(null, null, newAvatar);

        UserProfileResponse response = userService.updateProfile(testUser, request);
        assertEquals(newAvatar, response.avatarUrl());

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(newAvatar, reloaded.getAvatarUrl());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Họ tên rỗng -> 400 Bad Request")
    void testUpdateProfile_EmptyFullName_ThrowsBadRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("   ", null, null);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                userService.updateProfile(testUser, request));
        assertEquals("Full name cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("API 4 - Update Profile: Số điện thoại rỗng -> 400 Bad Request")
    void testUpdateProfile_EmptyPhone_ThrowsBadRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(null, "   ", null);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                userService.updateProfile(testUser, request));
        assertEquals("Phone number cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("API 4 - Update Profile: MockMvc HTTP PUT /api/users/me cập nhật thành công trả 200 OK")
    void testUpdateProfile_Http_Success() throws Exception {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("MockMvc Updated Name", "0911223344", "https://avatar.com/test.png");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("MockMvc Updated Name"))
                .andExpect(jsonPath("$.phone").value("0911223344"))
                .andExpect(jsonPath("$.avatarUrl").value("https://avatar.com/test.png"));
    }

    @Test
    @DisplayName("API 4 - Update Profile: Không đăng nhập -> 401 Unauthorized")
    void testUpdateProfile_Unauthenticated_ThrowsUnauthorized() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Name", "0900000000", null);
        assertThrows(UnauthorizedException.class, () ->
                userService.updateProfile(null, request));
    }

    // =========================================================================
    // BE01 TESTS: GIỚI TÍNH, EMAIL CHỈ ĐỌC, QUÊN/ĐỔI MẬT KHẨU, CHUYỂN VAI, PHIÊN
    // =========================================================================

    @Test
    @DisplayName("BE01: Cập nhật giới tính và kiểm tra email chỉ đọc")
    void testUpdateProfile_GenderAndEmailReadOnly_Success() throws Exception {
        String updateJson = """
            {
                "fullName": "Nguyen Van Test Updated",
                "phone": "0918889999",
                "gender": "Nữ",
                "email": "hacked_email@attacker.com"
            }
            """;

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van Test Updated"))
                .andExpect(jsonPath("$.phone").value("0918889999"))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.gender").value("FEMALE"));

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(testUser.getEmail(), reloaded.getEmail());
        assertEquals(Gender.FEMALE, reloaded.getGender());

        // Đăng nhập lại cũng phải trả về gender đúng
        LoginResponse loginRes = authService.login(new LoginRequest(testUser.getEmail(), rawPassword));
        assertEquals("FEMALE", loginRes.user().gender());
    }

    @Test
    @DisplayName("BE01: Quên mật khẩu qua email (Test Double) và đặt lại mật khẩu bằng token")
    void testForgotPasswordAndResetWithToken_Success() throws Exception {
        mockEmailService.clear();

        // 1. Gửi yêu cầu quên mật khẩu
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(testUser.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        String resetToken = mockEmailService.getLatestToken(testUser.getEmail());
        assertNotNull(resetToken, "Reset token must be delivered via EmailService test double");

        // 2. Thử đặt lại mật khẩu với token sai -> 400 Bad Request
        ResetPasswordWithTokenRequest badTokenReq = new ResetPasswordWithTokenRequest(
                testUser.getEmail(),
                "invalid-token-xyz",
                "NewPassword123@",
                "NewPassword123@"
        );
        mockMvc.perform(post("/api/auth/reset-password-with-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badTokenReq)))
                .andExpect(status().isBadRequest());

        // Mật khẩu cũ vẫn phải hoạt động
        assertDoesNotThrow(() -> authService.login(new LoginRequest(testUser.getEmail(), rawPassword)));

        // 3. Đặt lại mật khẩu với token hợp lệ
        ResetPasswordWithTokenRequest validReq = new ResetPasswordWithTokenRequest(
                testUser.getEmail(),
                resetToken,
                "NewPassword123@",
                "NewPassword123@"
        );
        mockMvc.perform(post("/api/auth/reset-password-with-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));

        // 4. Mật khẩu mới đăng nhập thành công
        LoginResponse newLogin = authService.login(new LoginRequest(testUser.getEmail(), "NewPassword123@"));
        assertNotNull(newLogin.accessToken());

        // 5. Mật khẩu cũ không còn hoạt động
        assertThrows(UnauthorizedException.class, () ->
                authService.login(new LoginRequest(testUser.getEmail(), rawPassword)));
    }

    @Test
    @DisplayName("BE01: Đăng xuất bị chặn khi khách hàng có đơn SEARCHING -> 409 Conflict kèm bookingId và status")
    void testLogout_BlockedWhenCustomerHasSearchingBooking_Returns409WithDetails() throws Exception {
        Booking booking = new Booking();
        booking.setCustomer(testUser);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setProblemDescription("Xe hết bình ắc quy");
        booking.setLatitude(10.776889);
        booking.setLongitude(106.695380);
        booking = bookingRepository.save(booking);

        // Service check
        ConflictException ex = assertThrows(ConflictException.class, () -> authService.logout(testUser));
        assertEquals(booking.getId(), ex.getBookingId());
        assertEquals("SEARCHING", ex.getBookingStatus());

        // HTTP MockMvc check
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
                .andExpect(jsonPath("$.bookingStatus").value("SEARCHING"));
    }

    @Test
    @DisplayName("BE01: Đăng xuất bị chặn khi thợ có đơn WAITING_PAYMENT -> 409 Conflict kèm bookingId và status")
    void testLogout_BlockedWhenProviderHasWaitingPaymentBooking_Returns409WithDetails() throws Exception {
        ProviderProfile profile = new ProviderProfile();
        profile.setUser(testUser);
        profile.setApprovalStatus(ApprovalStatus.APPROVED);
        profile.setActive(true);
        profile.setBaseLat(10.776889f);
        profile.setBaseLng(106.695380f);
        profile.setServiceRadiusKm(5.0f);
        profile = providerProfileRepository.saveAndFlush(profile);

        User customer = new User();
        customer.setFullName("Customer Test");
        customer.setEmail("cust_" + UUID.randomUUID() + "@motofix.vn");
        customer.setPhone("097" + (System.currentTimeMillis() % 10000000));
        customer.setPasswordHash(passwordEncoder.encode(rawPassword));
        customer.setStatus("active");
        customer = userRepository.save(customer);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(profile);
        booking.setStatus(BookingStatus.WAITING_PAYMENT);
        booking.setProblemDescription("Sửa xong chờ thanh toán");
        booking.setLatitude(10.776889);
        booking.setLongitude(106.695380);
        booking = bookingRepository.save(booking);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
                .andExpect(jsonPath("$.bookingStatus").value("WAITING_PAYMENT"));
    }

    @Test
    @DisplayName("BE01: Chuyển vai trò bị chặn khi đang có đơn mở -> 409 Conflict")
    void testSwitchRole_BlockedWhenActiveBooking_ThrowsConflict() throws Exception {
        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });
        testUser.getRoles().add(providerRole);
        testUser = userRepository.save(testUser);

        ProviderProfile profile = new ProviderProfile();
        profile.setUser(testUser);
        profile.setApprovalStatus(ApprovalStatus.APPROVED);
        profile.setActive(false);
        profile.setBaseLat(10.776889f);
        profile.setBaseLng(106.695380f);
        profile.setServiceRadiusKm(5.0f);
        providerProfileRepository.save(profile);

        // Khách có đơn SEARCHING
        Booking booking = new Booking();
        booking.setCustomer(testUser);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setProblemDescription("Xe xì lốp");
        booking.setLatitude(10.776889);
        booking.setLongitude(106.695380);
        booking = bookingRepository.save(booking);

        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("PROVIDER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
                .andExpect(jsonPath("$.bookingStatus").value("SEARCHING"));
    }

    @Test
    @DisplayName("BE01: Khách không có quyền thợ chuyển sang PROVIDER -> 400 Bad Request")
    void testSwitchRole_CustomerToProvider_WithoutRole_ThrowsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("PROVIDER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BE01: Thợ có offer ACCEPTED chuyển sang khách -> offline, offer EXPIRED, chọn thợ thất bại")
    void testSwitchRole_ProviderToCustomer_SetsOfflineAndExpiresOffers() throws Exception {
        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });
        testUser.getRoles().add(providerRole);
        testUser.setCurrentRole("PROVIDER");
        testUser = userRepository.save(testUser);

        ProviderProfile profile = new ProviderProfile();
        profile.setUser(testUser);
        profile.setApprovalStatus(ApprovalStatus.APPROVED);
        profile.setActive(true);
        profile.setBaseLat(10.776889f);
        profile.setBaseLng(106.695380f);
        profile.setServiceRadiusKm(5.0f);
        profile = providerProfileRepository.saveAndFlush(profile);

        String providerToken = jwtService.generateToken(testUser);

        // Khách khác tạo booking SEARCHING
        User customer = new User();
        customer.setFullName("Customer Other");
        customer.setEmail("cust_other_" + UUID.randomUUID() + "@motofix.vn");
        customer.setPhone("096" + (System.currentTimeMillis() % 10000000));
        customer.setPasswordHash(passwordEncoder.encode(rawPassword));
        customer.setStatus("active");
        customer = userRepository.save(customer);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setProblemDescription("Xe chết máy");
        booking.setLatitude(10.776889);
        booking.setLongitude(106.695380);
        booking = bookingRepository.save(booking);

        BookingOffer offer = new BookingOffer();
        offer.setBooking(booking);
        offer.setProvider(profile);
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setEstimatedMin(java.math.BigDecimal.valueOf(100000));
        offer.setEstimatedMax(java.math.BigDecimal.valueOf(200000));
        offer.setEstimatedArrivalMinutes(15);
        offer.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        offer = bookingOfferRepository.save(offer);

        // Thợ chuyển sang vai CUSTOMER
        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRole").value("CUSTOMER"));

        // Kiểm tra profile offline và offer EXPIRED
        ProviderProfile reloadedProfile = providerProfileRepository.findById(profile.getId()).orElseThrow();
        assertFalse(reloadedProfile.isActive(), "Provider must be offline after switching to CUSTOMER");

        BookingOffer reloadedOffer = bookingOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(OfferStatus.EXPIRED, reloadedOffer.getStatus(), "Pending/Accepted offers must be EXPIRED");

        // Khách thử chọn offer này -> thất bại vì không còn ACCEPTED
        final UUID bId = booking.getId();
        final UUID oId = offer.getId();
        final UUID cId = customer.getId();
        assertThrows(BadRequestException.class, () -> bookingService.selectOffer(bId, oId, cId));
    }

    @Test
    @DisplayName("BE01: Thợ đăng xuất làm hết hạn offer và vô hiệu hóa phiên cũ (không thể bật nhận đơn lại)")
    void testLogout_ExpiresOffersAndRevokesSession_OldTokenCannotActivateOnline() throws Exception {
        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });
        testUser.getRoles().add(providerRole);
        testUser.setCurrentRole("PROVIDER");
        testUser = userRepository.save(testUser);

        ProviderProfile profile = new ProviderProfile();
        profile.setUser(testUser);
        profile.setApprovalStatus(ApprovalStatus.APPROVED);
        profile.setActive(true);
        profile.setBaseLat(10.776889f);
        profile.setBaseLng(106.695380f);
        profile.setServiceRadiusKm(5.0f);
        profile = providerProfileRepository.saveAndFlush(profile);

        String activeProviderToken = jwtService.generateToken(testUser);

        User customer = new User();
        customer.setFullName("Customer Logout Test");
        customer.setEmail("cust_log_" + UUID.randomUUID() + "@motofix.vn");
        customer.setPhone("094" + (System.currentTimeMillis() % 10000000));
        customer.setPasswordHash(passwordEncoder.encode(rawPassword));
        customer.setStatus("active");
        customer = userRepository.save(customer);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.SEARCHING);
        booking.setProblemDescription("Bể bánh xe");
        booking.setLatitude(10.776889);
        booking.setLongitude(106.695380);
        booking = bookingRepository.save(booking);

        BookingOffer offer = new BookingOffer();
        offer.setBooking(booking);
        offer.setProvider(profile);
        offer.setStatus(OfferStatus.PENDING);
        offer.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        offer = bookingOfferRepository.save(offer);

        // Đăng xuất
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + activeProviderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Offer chuyển sang EXPIRED
        BookingOffer reloadedOffer = bookingOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(OfferStatus.EXPIRED, reloadedOffer.getStatus());

        // Phiên cũ dùng lại để bật nhận đơn -> 403 Forbidden
        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + activeProviderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BE01: Tab thợ cũ bị chặn thao tác khi user đã chuyển sang vai CUSTOMER -> 403 Forbidden")
    void testOldRoleTab_BlockedWhenRoleSwitched() throws Exception {
        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });
        testUser.getRoles().add(providerRole);
        testUser.setCurrentRole("PROVIDER");
        testUser = userRepository.save(testUser);

        ProviderProfile profile = new ProviderProfile();
        profile.setUser(testUser);
        profile.setApprovalStatus(ApprovalStatus.APPROVED);
        profile.setActive(false);
        profile.setBaseLat(10.776889f);
        profile.setBaseLng(106.695380f);
        profile.setServiceRadiusKm(5.0f);
        providerProfileRepository.save(profile);

        String sessionToken = jwtService.generateToken(testUser);

        // Chuyển sang CUSTOMER
        mockMvc.perform(patch("/api/users/me/current-role")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchRoleRequest("CUSTOMER"))))
                .andExpect(status().isOk());

        // Tab thợ cũ vẫn dùng token gửi lệnh bật nhận đơn -> bị chặn 403 Forbidden
        mockMvc.perform(patch("/api/providers/me/active")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BE01: Đăng nhập khôi phục đúng vai trò đã lưu (currentRole)")
    void testLogin_RestoresCurrentRole() throws Exception {
        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });
        testUser.getRoles().add(providerRole);
        testUser.setCurrentRole("PROVIDER");
        testUser = userRepository.save(testUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(testUser.getEmail(), rawPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.currentRole").value("PROVIDER"));
    }
}
