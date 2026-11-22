package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.SwitchRoleRequest;
import com.nhom6.motofix.dto.request.UpdateUserProfileRequest;
import com.nhom6.motofix.dto.respond.SwitchRoleResponse;
import com.nhom6.motofix.dto.respond.UserProfileResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ProviderProfile;
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
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final List<BookingStatus> ACTIVE_CUSTOMER_STATUSES = List.of(
            BookingStatus.SEARCHING,
            BookingStatus.SELECTED_PROVIDER,
            BookingStatus.PROVIDER_SELECTED,
            BookingStatus.PROVIDER_EN_ROUTE,
            BookingStatus.INSPECTING,
            BookingStatus.AWAITING_QUOTE_APPROVAL,
            BookingStatus.IN_PROGRESS,
            BookingStatus.WAITING_PAYMENT
    );

    public static final List<BookingStatus> ACTIVE_PROVIDER_STATUSES = List.of(
            BookingStatus.SELECTED_PROVIDER,
            BookingStatus.PROVIDER_SELECTED,
            BookingStatus.PROVIDER_EN_ROUTE,
            BookingStatus.INSPECTING,
            BookingStatus.AWAITING_QUOTE_APPROVAL,
            BookingStatus.IN_PROGRESS,
            BookingStatus.WAITING_PAYMENT
    );

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingOfferRepository bookingOfferRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final JwtService jwtService;

    /**
     * API 3: Xem thông tin cá nhân của người dùng đang đăng nhập.
     * GET /api/users/me
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserProfileResponse.from(user);
    }

    /**
     * API 4: Cập nhật thông tin cá nhân (họ tên, số điện thoại, avatar, giới tính).
     * PUT /api/users/me
     * Email là trường chỉ đọc, tuyệt đối không cho phép thay đổi qua API này.
     */
    @Transactional
    public UserProfileResponse updateProfile(User currentUser, UpdateUserProfileRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if ("locked".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Account is locked");
        }

        // Cập nhật họ tên nếu có
        if (request.fullName() != null) {
            String trimmedName = request.fullName().trim();
            if (trimmedName.isEmpty()) {
                throw new BadRequestException("Full name cannot be empty");
            }
            user.setFullName(trimmedName);
        }

        // Cập nhật số điện thoại nếu có và kiểm tra trùng lặp
        if (request.phone() != null) {
            String trimmedPhone = request.phone().trim();
            if (trimmedPhone.isEmpty()) {
                throw new BadRequestException("Phone number cannot be empty");
            }
            if (!trimmedPhone.equals(user.getPhone())) {
                userRepository.findByPhone(trimmedPhone).ifPresent(otherUser -> {
                    if (!otherUser.getId().equals(user.getId())) {
                        throw new BadRequestException("Phone already exists");
                    }
                });
                user.setPhone(trimmedPhone);
            }
        }

        // Cập nhật URL ảnh đại diện nếu có
        if (request.avatarUrl() != null) {
            String trimmedAvatar = request.avatarUrl().trim();
            user.setAvatarUrl(trimmedAvatar.isEmpty() ? null : trimmedAvatar);
        }

        // Cập nhật giới tính nếu có
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(Gender.fromString(request.gender()));
        }

        User savedUser = userRepository.save(user);
        log.info("User {} updated profile successfully", savedUser.getId());

        return UserProfileResponse.from(savedUser);
    }

    /**
     * Chuyển đổi vai trò hiện tại (CUSTOMER <-> PROVIDER)
     * PATCH /api/users/me/current-role
     */
    @Transactional
    public SwitchRoleResponse switchRole(User currentUser, SwitchRoleRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String targetRole = request.targetRole().trim().toUpperCase();
        if (!"CUSTOMER".equals(targetRole) && !"PROVIDER".equals(targetRole)) {
            throw new BadRequestException("Target role must be either CUSTOMER or PROVIDER");
        }

        // Kiểm tra quyền khi muốn chuyển sang vai PROVIDER
        if ("PROVIDER".equals(targetRole)) {
            boolean hasProviderRole = user.getRoles().stream()
                    .anyMatch(r -> "PROVIDER".equalsIgnoreCase(r.getName()));
            boolean hasApprovedProfile = providerProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .orElse(false);

            if (!hasProviderRole && !hasApprovedProfile) {
                throw new BadRequestException("Tài khoản chưa được phê duyệt quyền thợ sửa xe");
            }
        }

        // Kiểm tra đơn mở ở cả hai vai
        checkNoActiveBookings(user, "Không thể chuyển vai trò khi đang có đơn cứu hộ chưa hoàn tất");

        // Khi rời vai thợ sang vai khách: tự động offline và expire offer chưa được chọn cùng transaction
        if ("CUSTOMER".equals(targetRole)) {
            ProviderProfile profile = providerProfileRepository.findByUserId(user.getId()).orElse(null);
            if (profile != null) {
                profile.setActive(false);
                providerProfileRepository.saveAndFlush(profile);

                bookingOfferRepository.expireOffersForProvider(
                        profile.getId(),
                        OfferStatus.EXPIRED,
                        List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED),
                        BookingStatus.SEARCHING
                );
                log.info("Provider {} switched to CUSTOMER: set offline and expired pending/accepted offers", profile.getId());
            }
        }

        user.setCurrentRole(targetRole);
        userRepository.save(user);

        String newToken = jwtService.generateToken(user);
        log.info("User {} switched currentRole to {}", user.getId(), targetRole);

        return new SwitchRoleResponse(targetRole, newToken, "Chuyển vai trò thành công");
    }

    /**
     * Kiểm tra đơn mở ở cả 2 vai cho một người dùng.
     * Ném ConflictException kèm theo bookingId và bookingStatus nếu phát hiện đơn active.
     */
    @Transactional(readOnly = true)
    public void checkNoActiveBookings(User user, String errorMessagePrefix) {
        // 1. Phía khách hàng: đơn từ SEARCHING tới WAITING_PAYMENT
        List<Booking> customerBookings = bookingRepository.findActiveBookingsByCustomerId(
                user.getId(),
                ACTIVE_CUSTOMER_STATUSES
        );
        if (!customerBookings.isEmpty()) {
            Booking b = customerBookings.get(0);
            throw new ConflictException(
                    errorMessagePrefix + ": Đơn khách " + b.getId() + " đang ở trạng thái " + b.getStatus(),
                    b.getId(),
                    b.getStatus().name()
            );
        }

        // 2. Phía thợ: đơn đã được giao và chưa kết thúc (SELECTED_PROVIDER .. WAITING_PAYMENT)
        ProviderProfile profile = providerProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile != null) {
            List<Booking> providerBookings = bookingRepository.findActiveBookingsByProviderId(
                    profile.getId(),
                    ACTIVE_PROVIDER_STATUSES
            );
            if (!providerBookings.isEmpty()) {
                Booking b = providerBookings.get(0);
                throw new ConflictException(
                        errorMessagePrefix + ": Công việc thợ " + b.getId() + " đang ở trạng thái " + b.getStatus(),
                        b.getId(),
                        b.getStatus().name()
                );
            }
        }
    }
}
