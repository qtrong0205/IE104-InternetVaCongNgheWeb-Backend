package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderActiveRequest;
import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.IncomingOfferResponse;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.mapper.ProviderProfileMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderService {
    public static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.SELECTED_PROVIDER,
            BookingStatus.PROVIDER_SELECTED,
            BookingStatus.PROVIDER_EN_ROUTE,
            BookingStatus.INSPECTING,
            BookingStatus.AWAITING_QUOTE_APPROVAL,
            BookingStatus.IN_PROGRESS,
            BookingStatus.WAITING_PAYMENT
    );

    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderProfileMapper providerProfileMapper;
    private final BookingOfferRepository bookingOfferRepository;
    private final BookingRepository bookingRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProviderProfileResponse uploadProfile(
            User user,
            ProviderProfileRequest request
    ) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        UUID userId = user.getId();

        // Kiểm tra profile tồn tại chưa
        if (providerProfileRepository.existsByUserId(userId)) {
            throw new BadRequestException("Profile already existed");
        }

        // Tạo entity từ request và lưu vào repository
        var providerProfile = providerProfileMapper.toEntity(request);
        providerProfile.setUser(user);
        providerProfile.setApprovalStatus(ApprovalStatus.PENDING);
        providerProfile.setActive(false);
        providerProfile.setRejectionReason(null);
        providerProfileRepository.save(providerProfile);

        return providerProfileMapper.toResponse(providerProfile);
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getMyProfile(User user) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }
        ProviderProfile provider = providerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        return providerProfileMapper.toResponse(provider);
    }

    @Transactional
    public ProviderProfileResponse updateMyProfile(User user, ProviderProfileRequest request) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }

        // Khóa bi quan (pessimistic lock) trên hồ sơ provider để tránh race condition
        ProviderProfile provider = providerProfileRepository.findByUserIdWithLock(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        // 1. Kiểm tra trạng thái online: nếu đang online thì từ chối cập nhật
        if (provider.isActive()) {
            throw new BadRequestException("Không thể cập nhật hồ sơ khi đang bật nhận đơn (online). Vui lòng tắt nhận đơn trước.");
        }

        // 2. Kiểm tra booking đang xử lý: nếu có booking chưa kết thúc thì từ chối cập nhật
        if (bookingRepository.hasActiveBookings(provider.getId(), ACTIVE_BOOKING_STATUSES)) {
            throw new BadRequestException("Không thể cập nhật hồ sơ khi đang có đơn hàng đang trong quá trình xử lý.");
        }

        // Cập nhật các thông tin được phép sửa
        provider.setBusinessName(request.businessName());
        provider.setIdDocumentUrl(request.idDocumentUrl());
        provider.setBusinessLicenseUrl(request.businessLicenseUrl());
        provider.setBaseLat(request.baseLat());
        provider.setBaseLng(request.baseLng());
        if (request.serviceRadiusKm() != null) {
            provider.setServiceRadiusKm(request.serviceRadiusKm());
        }

        // Chuyển trạng thái duyệt về PENDING, đảm bảo is_active = false và xóa lý do từ chối
        provider.setApprovalStatus(ApprovalStatus.PENDING);
        provider.setActive(false);
        provider.setRejectionReason(null);

        ProviderProfile saved = providerProfileRepository.save(provider);
        return providerProfileMapper.toResponse(saved);
    }

    @Transactional
    public ProviderProfileResponse updateActiveStatus(User user, ProviderActiveRequest request) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }

        ProviderProfile provider = providerProfileRepository.findByUserIdWithLock(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        boolean requestedActive = Boolean.TRUE.equals(request.isActive());

        if (requestedActive) {
            // Chỉ provider APPROVED mới được bật nhận đơn (online)
            if (provider.getApprovalStatus() != ApprovalStatus.APPROVED) {
                throw new BadRequestException("Chỉ thợ đã được phê duyệt (APPROVED) mới được phép bật nhận đơn.");
            }
            provider.setActive(true);
        } else {
            provider.setActive(false);
        }

        ProviderProfile saved = providerProfileRepository.save(provider);
        return providerProfileMapper.toResponse(saved);
    }

    // Lấy danh sách pending
    @Transactional(readOnly = true)
    public Page<ProviderProfileResponse> getPendingProviders(Pageable pageable) {
        return providerProfileRepository
                .findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(providerProfileMapper::toResponse);
    }

    // Approve provider
    @Transactional
    public ProviderProfileResponse approveProvider(UUID providerId) {
        ProviderProfile provider = providerProfileRepository
                .findById(providerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Provider not found")
                );

        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending providers can be approved"
            );
        }

        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        provider.setRejectionReason(null);
        provider.setActive(true);

        User u = provider.getUser();
        if (u != null && roleRepository != null && userRepository != null) {
            roleRepository.findByName("PROVIDER").ifPresent(role -> {
                if (!u.getRoles().contains(role)) {
                    u.getRoles().add(role);
                    userRepository.save(u);
                }
            });
        }

        ProviderProfile savedProvider = providerProfileRepository.save(provider);
        return providerProfileMapper.toResponse(savedProvider);
    }

    // Reject provider
    @Transactional
    public ProviderProfileResponse rejectProvider(
            UUID providerId,
            String reason
    ) {
        ProviderProfile provider = providerProfileRepository
                .findById(providerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Provider not found")
                );

        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending providers can be rejected"
            );
        }

        provider.setApprovalStatus(ApprovalStatus.REJECTED);
        provider.setRejectionReason(reason);
        provider.setActive(false);

        ProviderProfile savedProvider = providerProfileRepository.save(provider);
        return providerProfileMapper.toResponse(savedProvider);
    }

    @Transactional(readOnly = true)
    public List<IncomingOfferResponse> getIncomingOffers(User user) {
        UUID providerId = providerProfileRepository.findIdByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));

        LocalDateTime now = LocalDateTime.now();

        return bookingOfferRepository.findByProvider_IdAndStatus(providerId, OfferStatus.PENDING)
                .stream()
                .filter(o -> o.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(BookingOffer::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    private IncomingOfferResponse toResponse(BookingOffer o) {
        Booking b = o.getBooking();
        return new IncomingOfferResponse(
                o.getId(),
                b.getId(),
                b.getProblemDescription(),
                b.getLatitude(),
                b.getLongitude(),
                o.getDistanceM(),
                o.getCreatedAt(),
                o.getExpiresAt()
        );
    }
}
