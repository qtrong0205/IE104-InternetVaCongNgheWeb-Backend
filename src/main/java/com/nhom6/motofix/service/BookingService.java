package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.entity.BookingMedia;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.NearbyProviderView;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.BookingMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingMapper bookingMapper;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final BookingOfferRepository bookingOfferRepository;

    @Value("${booking.offer-radius-m}")
    private Double searchRadiusM;

    @Value("${booking.offer-max-providers}")
    private Integer maxProviders;

    @Value("${booking.offer-ttl}")
    private Duration offerTtl;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var booking = bookingMapper.toBooking(request);
        booking.setCustomer(user);

        if (request.media() != null) {
            request.media().forEach(item -> {
                var media = new BookingMedia();
                media.setUrl(item.url());
                media.setType(item.type());
                booking.addMedia(media);
            });
        }

        var saved = bookingRepository.save(booking);

        // Tìm top N provider gần nhất
        List<NearbyProviderView> providers = providerProfileRepository.findNearby(
                request.latitude(), request.longitude(), searchRadiusM, maxProviders);

        // Tạo offer PENDING cho từng provider, trỏ vào booking đã lưu
        LocalDateTime expiresAt = LocalDateTime.now().plus(offerTtl);

        List<BookingOffer> offers = providers.stream().map(p -> {
            BookingOffer offer = new BookingOffer();
            offer.setBooking(saved);
            offer.setProvider(providerProfileRepository.getReferenceById(p.getId()));
            offer.setStatus(OfferStatus.PENDING);
            offer.setDistanceM(p.getDistanceM());
            offer.setExpiresAt(expiresAt);
            return offer;
        }).toList();

        bookingOfferRepository.saveAll(offers);

        return bookingMapper.toBookingResponse(saved);
    }
}
