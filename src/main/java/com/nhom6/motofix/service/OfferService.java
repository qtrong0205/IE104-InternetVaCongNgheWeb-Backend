package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.AcceptOfferRequest;
import com.nhom6.motofix.dto.request.DeclineOfferRequest;
import com.nhom6.motofix.dto.respond.OfferResultResponse;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.OfferMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService {
    private final BookingOfferRepository offerRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final OfferMapper offerMapper;

    @Transactional
    public OfferResultResponse reject(UUID offerId, DeclineOfferRequest request, User user) {
        BookingOffer offer = loadRespondableOffer(offerId, user);

        offer.setNote(request == null ? null : request.note());
        offer.setStatus(OfferStatus.DECLINED);

        return offerMapper.toOfferResultResponse(offer);
    }

    @Transactional
    public OfferResultResponse accept(UUID offerId, AcceptOfferRequest request, User user) {
        BookingOffer offer = loadRespondableOffer(offerId, user);

        if (request.estimatedMax().compareTo(request.estimatedMin()) < 0) {
            throw new BadRequestException("estimatedMax must be greater than or equal to estimatedMin");
        }

        offer.setEstimatedMin(request.estimatedMin());
        offer.setEstimatedMax(request.estimatedMax());
        offer.setEstimatedArrivalMinutes(request.estimatedArrivalMinutes());
        offer.setNote(request.note());
        offer.setStatus(OfferStatus.ACCEPTED);

        return offerMapper.toOfferResultResponse(offer);
    }

    // Dùng chung cho accept và reject
    private BookingOffer loadRespondableOffer(UUID offerId, User user) {
        var provider = providerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not yet a provider"));

        // Tìm theo cả offerId và provider của người đăng nhập, offer của thợ khác sẽ ra 404
        var offer = offerRepository.findByIdAndProvider_Id(offerId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Offer not exists"));

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