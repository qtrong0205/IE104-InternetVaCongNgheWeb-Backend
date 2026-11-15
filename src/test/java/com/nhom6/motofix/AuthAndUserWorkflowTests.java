package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.LoginRequest;
import com.nhom6.motofix.dto.request.ResetPasswordRequest;
import com.nhom6.motofix.dto.request.UpdateUserProfileRequest;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.UserProfileResponse;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.AuthService;
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
}
