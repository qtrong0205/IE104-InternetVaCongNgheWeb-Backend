package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderActiveRequest;
import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.IncomingOfferResponse;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.dto.respond.PublicProviderProfileResponse;
import com.nhom6.motofix.dto.respond.ProviderRevenueResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.enums.QuoteStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.mapper.BookingMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final BookingMapper bookingMapper;

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
        providerProfile.setAddress(request.address());
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

    @Transactional(readOnly = true)
    public PublicProviderProfileResponse getPublicProfile(UUID providerId) {
        ProviderProfile provider = providerProfileRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        if (provider.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ResourceNotFoundException("Hồ sơ thợ không tồn tại hoặc chưa được phê duyệt");
        }

        return providerProfileMapper.toPublicResponse(provider);
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
        provider.setAddress(request.address());
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

        if (user.getCurrentRole() != null && "CUSTOMER".equalsIgnoreCase(user.getCurrentRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Tài khoản hiện đang ở vai trò khách hàng, không thể thực hiện thao tác của thợ");
        }

        boolean requestedActive = Boolean.TRUE.equals(request.isActive());

        if (requestedActive) {
            // Chỉ provider APPROVED mới được bật nhận đơn (online)
            if (provider.getApprovalStatus() != ApprovalStatus.APPROVED) {
                throw new BadRequestException("Chỉ thợ đã được phê duyệt (APPROVED) mới được phép bật nhận đơn.");
            }
            provider.setActive(true);
        } else {
            // Tắt online: cho phép cả khi đang làm việc.
            // Hết hạn mọi offer chưa được khách chọn (PENDING / ACCEPTED trên booking SEARCHING)
            provider.setActive(false);
            bookingOfferRepository.expireOffersForProvider(
                    provider.getId(),
                    OfferStatus.EXPIRED,
                    List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED),
                    BookingStatus.SEARCHING
            );
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
                .findByIdWithLock(providerId)
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
        // Sau khi admin duyệt: cấp quyền thợ và giữ offline đến khi thợ tự bật
        provider.setActive(false);

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
        if (reason == null || reason.trim().isBlank()) {
            throw new BadRequestException("Rejection reason is required");
        }

        ProviderProfile provider = providerProfileRepository
                .findByIdWithLock(providerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Provider not found")
                );

        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending providers can be rejected"
            );
        }

        provider.setApprovalStatus(ApprovalStatus.REJECTED);
        provider.setRejectionReason(reason.trim());
        provider.setActive(false);

        ProviderProfile savedProvider = providerProfileRepository.save(provider);
        return providerProfileMapper.toResponse(savedProvider);
    }

    @Transactional(readOnly = true)
    public List<IncomingOfferResponse> getIncomingOffers(User user) {
        UUID providerId = providerProfileRepository.findIdByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));

        LocalDateTime now = LocalDateTime.now();

        // Đọc cả offer PENDING còn hạn và các offer ACCEPTED đang chờ khách chọn trên đơn SEARCHING
        return bookingOfferRepository.findIncomingOffersForProvider(providerId, now, BookingStatus.SEARCHING)
                .stream()
                .sorted(Comparator.comparing(BookingOffer::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncomingOfferResponse getOfferDetails(UUID offerId, User user) {
        UUID providerId = providerProfileRepository.findIdByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));

        BookingOffer offer = bookingOfferRepository.findByIdAndProvider_Id(offerId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not exists: " + offerId));

        return toResponse(offer);
    }

    private IncomingOfferResponse toResponse(BookingOffer o) {
        Booking b = o.getBooking();
        List<com.nhom6.motofix.dto.respond.UploadMediaResponse> mediaResponses = b.getMedia() != null ?
                b.getMedia().stream()
                        .map(m -> new com.nhom6.motofix.dto.respond.UploadMediaResponse(m.getUrl(), m.getType()))
                        .toList() : null;

        return new IncomingOfferResponse(
                o.getId(),
                b.getId(),
                o.getStatus(),
                b.getProblemDescription(),
                b.getAddress(),
                b.getLatitude(),
                b.getLongitude(),
                b.getVehicleBrand(),
                b.getVehicleModel(),
                b.getVehicleLicensePlate(),
                mediaResponses,
                o.getDistanceM(),
                o.getCreatedAt(),
                o.getExpiresAt(),
                o.getEstimatedMin(),
                o.getEstimatedMax(),
                o.getEstimatedArrivalMinutes(),
                o.getNote()
        );
    }

    /**
     * Provider xem lịch sử các đơn đã kết thúc (COMPLETED và CANCELLED) của mình.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getProviderBookings(User user, BookingStatus status, Pageable pageable) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }

        ProviderProfile provider = providerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        Set<BookingStatus> allowedStatuses = Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED);
        if (status != null) {
            if (!allowedStatuses.contains(status)) {
                throw new BadRequestException("Lịch sử đơn chỉ bao gồm các đơn đã kết thúc (COMPLETED hoặc CANCELLED)");
            }
            return bookingRepository.findByProviderIdAndStatus(provider.getId(), status, pageable)
                    .map(bookingMapper::toBookingResponse);
        }

        return bookingRepository.findByProviderIdAndStatusIn(provider.getId(), allowedStatuses, pageable)
                .map(bookingMapper::toBookingResponse);
    }

    /**
     * Provider xem doanh thu theo ngày cụ thể (mặc định hôm nay).
     * Chỉ tính từ các đơn COMPLETED được xác nhận thanh toán trong ngày đó.
     */
    @Transactional(readOnly = true)
    public ProviderRevenueResponse getProviderRevenue(User user, LocalDate date) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác này");
        }

        ProviderProfile provider = providerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ thợ không tồn tại"));

        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        List<Booking> completedBookings = bookingRepository.findCompletedBookingsWithQuotesByProviderAndDateRange(
                provider.getId(),
                BookingStatus.COMPLETED,
                startOfDay,
                endOfDay
        );

        BigDecimal totalRevenue = completedBookings.stream()
                .map(b -> b.getQuotes().stream()
                        .filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                        .map(RepairQuote::getTotalAmount)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BookingResponse> bookingResponses = completedBookings.stream()
                .map(bookingMapper::toBookingResponse)
                .toList();

        return new ProviderRevenueResponse(
                targetDate,
                totalRevenue,
                completedBookings.size(),
                bookingResponses
        );
    }
}
