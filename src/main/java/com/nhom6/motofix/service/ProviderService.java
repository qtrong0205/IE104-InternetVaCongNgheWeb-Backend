package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.IncomingOfferResponse;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.ProviderProfileMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderService {
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderProfileMapper providerProfileMapper;
    private final BookingOfferRepository bookingOfferRepository;

    public ProviderProfileResponse uploadProfile(
        User user,
        ProviderProfileRequest request
    ){
        // Lấy user id
        UUID userId = user.getId();

        // Kiểm trả profile tồn tại chưa
        if(providerProfileRepository.existsByUserId(userId)){
            throw new BadRequestException("Profile already existed");
        }

        // Tạo entity từ request và lưu vào repository
        var providerProfile = providerProfileMapper.toEntity(request);
        providerProfile.setUser(user);
        providerProfileRepository.save(providerProfile);

        return providerProfileMapper.toResponse(providerProfile);
    }

    // Lấy danh sách pending
    @Transactional(readOnly =true )
    public Page<ProviderProfileResponse> getPendingProviders(Pageable pageable) {
        return providerProfileRepository
                .findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(providerProfileMapper::toResponse);
    }

    //Approve provider
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

        ProviderProfile savedProvider =
                providerProfileRepository.save(provider);

        return providerProfileMapper.toResponse(savedProvider);
    }

    //Reject provider
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

        ProviderProfile savedProvider =
                providerProfileRepository.save(provider);

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
