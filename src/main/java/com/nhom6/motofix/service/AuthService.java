package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ForgotPasswordRequest;
import com.nhom6.motofix.dto.request.LoginRequest;
import com.nhom6.motofix.dto.request.RegisterRequest;
import com.nhom6.motofix.dto.request.ResetPasswordRequest;
import com.nhom6.motofix.dto.request.ResetPasswordWithTokenRequest;
import com.nhom6.motofix.dto.respond.LoginResponse;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.RegisterResponse;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.mapper.UserMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;
    private final ProviderProfileRepository providerProfileRepository;
    private final BookingOfferRepository bookingOfferRepository;
    private final EmailService emailService;
    private final WebSocketSyncService webSocketSyncService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Normalize dữ liệu
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();

        // 2. Kiểm tra email
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        // 3. Kiểm tra phone
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Phone already exists");
        }

        // 4. Tìm role CUSTOMER hoặc USER
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .or(() -> roleRepository.findByName("USER"))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("CUSTOMER");
                    return roleRepository.save(r);
                });

        // 5. Tạo user
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCurrentRole("CUSTOMER");
        user.setTokenVersion(0);
        user.getRoles().add(customerRole);

        // 6. Lưu user
        User savedUser = userRepository.save(user);

        return userMapper.toResponse(savedUser);
    }

    /**
     * Xử lý đăng nhập, khôi phục vai đã lưu và khởi tạo JWT Token
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 1. Chuẩn hóa email tìm kiếm
        String email = request.email().trim().toLowerCase();

        // 2. Tìm kiếm User theo email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid email or password")
                );

        // 3. Kiểm tra trạng thái tài khoản
        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // 4. Kiểm tra tính hợp lệ của mật khẩu
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // 5. Khôi phục vai đã lưu
        if (user.getCurrentRole() == null || user.getCurrentRole().isBlank()) {
            user.setCurrentRole("CUSTOMER");
            user = userRepository.save(user);
        }

        // 6. Tạo JWT Token
        String accessToken = jwtService.generateToken(user);

        // 7. Ánh xạ thông tin UserInfo
        LoginResponse.UserInfo userInfo = userMapper.toLoginUserInfo(user);

        return new LoginResponse(accessToken, "Bearer", userInfo);
    }

    /**
     * API: Đăng xuất người dùng.
     * Quy tắc:
     * - Kiểm tra đơn mở ở cả 2 vai: nếu có đơn chưa hoàn tất, ném ConflictException (trả ID và status) và không đăng xuất.
     * - Nếu hợp lệ: nếu tài khoản có hồ sơ thợ, tự động offline và expire các offer PENDING/ACCEPTED trên booking SEARCHING.
     * - Tăng tokenVersion để kết thúc hiệu lực phiên hiện tại (chặn dùng lại token cũ).
     * - Xóa SecurityContext.
     */
    @Transactional
    public MessageResponse logout(User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Kiểm tra đơn mở ở cả 2 vai
        userService.checkNoActiveBookings(user, "Không thể đăng xuất khi đang có đơn cứu hộ chưa hoàn tất");

        // 2. Nếu là thợ: đặt offline và expire offer chưa được chọn cùng giao dịch
        ProviderProfile profile = providerProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile != null) {
            List<BookingOffer> waitingOffers = bookingOfferRepository.findWaitingOffersForProvider(
                    profile.getId(),
                    List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED),
                    BookingStatus.SEARCHING
            );
            profile.setActive(false);
            providerProfileRepository.saveAndFlush(profile);

            bookingOfferRepository.expireOffersForProvider(
                    profile.getId(),
                    OfferStatus.EXPIRED,
                    List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED),
                    BookingStatus.SEARCHING
            );
            for (BookingOffer o : waitingOffers) {
                webSocketSyncService.notifyOfferExpired(profile.getId(), o.getId(), o.getBooking().getId());
                webSocketSyncService.notifyOfferExpiredForBooking(o.getBooking().getId(), o.getId());
            }
            log.info("Provider {} logged out: set offline and expired waiting offers", profile.getId());
        }

        // 3. Thu hồi hiệu lực phiên bằng cách tăng tokenVersion
        int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(currentVersion + 1);
        userRepository.save(user);

        // 4. Xóa SecurityContext
        SecurityContextHolder.clearContext();
        log.info("User {} logged out successfully, tokenVersion bumped to {}", user.getId(), user.getTokenVersion());

        return new MessageResponse("Logged out successfully");
    }

    /**
     * API: Quên mật khẩu qua email từ trang login
     * Tạo mã reset token có thời hạn 15 phút và gửi qua EmailService (test double)
     */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email does not exist"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(email, resetToken);
        log.info("Generated password reset token for user {}", user.getId());

        return new MessageResponse("Password reset instructions have been sent to your email");
    }

    /**
     * API: Hoàn tất đặt lại mật khẩu bằng reset token nhận từ email
     */
    @Transactional
    public MessageResponse resetPasswordWithToken(ResetPasswordWithTokenRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email does not exist"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        if (user.getPasswordResetToken() == null || !user.getPasswordResetToken().equals(request.token().trim())) {
            throw new BadRequestException("Invalid or expired password reset token");
        }

        if (user.getPasswordResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getPasswordResetTokenExpiry())) {
            throw new BadRequestException("Password reset token has expired");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation password do not match");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must not be the same as the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);

        // Invalidate old sessions
        int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(currentVersion + 1);

        userRepository.save(user);
        log.info("User {} reset password with token successfully", user.getId());

        return new MessageResponse("Password has been reset successfully");
    }

    /**
     * API 2: Đổi mật khẩu trong phiên (đã đăng nhập)
     */
    @Transactional
    public MessageResponse resetPassword(User currentUser, ResetPasswordRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // 1. Xác thực mật khẩu hiện tại
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // 2. Kiểm tra mật khẩu mới và xác nhận mật khẩu mới có trùng khớp không
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation password do not match");
        }

        // 3. Không cho phép đặt mật khẩu mới trùng mật khẩu hiện tại
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must not be the same as the current password");
        }

        // 4. Mã hóa mật khẩu mới và lưu vào CSDL
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new MessageResponse("Password reset successfully");
    }
}
