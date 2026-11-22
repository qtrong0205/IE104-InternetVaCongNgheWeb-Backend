package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.request.DeclineOfferRequest;
import com.nhom6.motofix.dto.respond.OfferResultResponse;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.enums.SyncEventType;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.OfferMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService {
    private final BookingOfferRepository offerRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final BookingRepository bookingRepository;
    private final OfferMapper offerMapper;
    private final WebSocketSyncService webSocketSyncService;

    @Transactional
    public OfferResultResponse reject(UUID offerId, DeclineOfferRequest request, User user) {
        BookingOffer offer = loadRespondableOffer(offerId, user);

        offer.setNote(request == null ? null : request.note());
        offer.setStatus(OfferStatus.DECLINED);
        BookingOffer saved = offerRepository.save(offer);

        webSocketSyncService.notifyBookingStatusChanged(
                offer.getBooking(),
                SyncEventType.OFFER_DECLINED,
                user,
                "Thợ đã từ chối yêu cầu cứu hộ",
                offer.getId()
        );

        return offerMapper.toOfferResultResponse(saved);
    }

    @Transactional
    public OfferResultResponse accept(UUID offerId, AcceptOfferRequest request, User user) {
        BookingOffer offer = loadRespondableOffer(offerId, user);

        if (request.estimatedMin() == null || request.estimatedMin().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Giá ước tính nhỏ nhất phải lớn hơn 0");
        }
        if (request.estimatedMax() == null || request.estimatedMax().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Giá ước tính lớn nhất phải lớn hơn 0");
        }
        if (request.estimatedMax().compareTo(request.estimatedMin()) < 0) {
            throw new BadRequestException("Giá ước tính lớn nhất phải lớn hơn hoặc bằng giá nhỏ nhất");
        }
        if (request.estimatedArrivalMinutes() == null || request.estimatedArrivalMinutes() <= 0) {
            throw new BadRequestException("Thời gian đến ước tính phải là số nguyên dương");
        }

        offer.setEstimatedMin(request.estimatedMin());
        offer.setEstimatedMax(request.estimatedMax());
        offer.setEstimatedArrivalMinutes(request.estimatedArrivalMinutes());
        offer.setNote(request.note());
        offer.setStatus(OfferStatus.ACCEPTED);
        BookingOffer saved = offerRepository.save(offer);

        webSocketSyncService.notifyOfferAccepted(offer.getBooking(), saved, user);

        return offerMapper.toOfferResultResponse(saved);
    }

    // Dùng chung cho accept và reject
    private BookingOffer loadRespondableOffer(UUID offerId, User user) {
        if (user.getCurrentRole() != null && "CUSTOMER".equalsIgnoreCase(user.getCurrentRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Tài khoản hiện đang ở vai trò khách hàng, không thể thực hiện thao tác của thợ");
        }

        var provider = providerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not yet a provider"));

        if (provider.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BadRequestException("Hồ sơ thợ chưa được phê duyệt");
        }

        if (!provider.isActive()) {
            throw new BadRequestException("Thợ đang ở trạng thái offline, không thể thao tác với offer");
        }

        if (bookingRepository.hasActiveBookings(provider.getId(), UserService.ACTIVE_PROVIDER_STATUSES)) {
            throw new ConflictException("Thợ đang có công việc cứu hộ đang thực hiện, không thể nhận thêm yêu cầu");
        }

        // Tìm theo cả offerId và provider của người đăng nhập, offer của thợ khác sẽ ra 404
        var offer = offerRepository.findByIdAndProvider_Id(offerId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Offer not exists: " + offerId));

        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new BadRequestException("Offer has already been responded to or has expired");
        }
        if (!offer.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Offer has expired");
        }
        if (offer.getBooking().getStatus() != BookingStatus.SEARCHING) {
            throw new BadRequestException("Booking is no longer accepting offers");
        }
        return offer;
    }
}