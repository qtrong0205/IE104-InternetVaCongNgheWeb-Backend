package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CancelBookingRequest;
import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.respond.BookingOfferSummaryResponse;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingMedia;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.NearbyProviderView;
import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.enums.QuoteStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.mapper.BookingMapper;
import com.nhom6.motofix.repository.BookingOfferRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.RepairQuoteRepository;
import com.nhom6.motofix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RepairQuoteRepository quoteRepository;

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

    // Lấy danh sách offer Accepted
    @Transactional(readOnly = true)
    public List<BookingOfferSummaryResponse> getAcceptedOffers(UUID bookingId, UUID customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedException("You do not have permission to view offers for this booking");
        }

        List<BookingOffer> offers = bookingOfferRepository.findByBookingIdAndStatusWithProvider(
                bookingId, OfferStatus.ACCEPTED);

        return offers.stream().map(offer -> {
            var provider = offer.getProvider();
            var user = provider.getUser();
            return new BookingOfferSummaryResponse(
                    offer.getId(),
                    offer.getId(),
                    booking.getId(),
                    offer.getStatus(),
                    offer.getEstimatedMin(),
                    offer.getEstimatedMax(),
                    offer.getEstimatedArrivalMinutes(),
                    offer.getNote(),
                    offer.getDistanceM(),
                    offer.getCreatedAt(),
                    offer.getExpiresAt(),
                    provider.getId(),
                    provider.getBusinessName(),
                    user != null ? user.getFullName() : null,
                    user != null ? user.getPhone() : null,
                    user != null ? user.getAvatarUrl() : null,
                    provider.getRatingAvg(),
                    provider.getRatingCount(),
                    provider.getBaseLat(),
                    provider.getBaseLng()
            );
        }).toList();
    }

    //Chọn thợ cho booking
    @Transactional
    public BookingResponse selectOffer(UUID bookingId, UUID offerId, UUID customerId) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedException("You do not have permission to select an offer for this booking");
        }

        if (booking.getStatus() != BookingStatus.SEARCHING) {
            throw new BadRequestException("Booking is no longer in SEARCHING status or a provider has already been selected");
        }

        BookingOffer offer = bookingOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));

        if (!offer.getBooking().getId().equals(bookingId)) {
            throw new BadRequestException("Offer does not belong to this booking");
        }

        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new BadRequestException("Only ACCEPTED offers can be selected");
        }

        booking.setProvider(offer.getProvider());
        booking.setStatus(BookingStatus.PROVIDER_SELECTED);
        Booking saved = bookingRepository.save(booking);

        // Expire all other offers (PENDING, ACCEPTED)
        bookingOfferRepository.expireRemainingOffers(
                bookingId,
                offerId,
                OfferStatus.EXPIRED,
                List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED)
        );

        log.info("Customer {} selected offer {} (provider {}) for booking {}", customerId, offerId, offer.getProvider().getId(), bookingId);

        return bookingMapper.toBookingResponse(saved);
    }

    @Transactional
    public BookingResponse confirmEnRoute(UUID bookingId, User user) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        if (booking.getStatus() != BookingStatus.SELECTED_PROVIDER && booking.getStatus() != BookingStatus.PROVIDER_SELECTED) {
            throw new ConflictException("Booking is in " + booking.getStatus() + " status, cannot move to PROVIDER_EN_ROUTE");
        }

        booking.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        Booking saved = bookingRepository.save(booking);
        log.info("Provider {} is en-route for booking {}", user.getId(), bookingId);

        return bookingMapper.toBookingResponse(saved);
    }

    @Transactional
    public BookingResponse confirmArrived(UUID bookingId, User user) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        if (booking.getStatus() != BookingStatus.PROVIDER_EN_ROUTE) {
            throw new ConflictException("Booking is in " + booking.getStatus() + " status, cannot move to INSPECTING");
        }

        booking.setStatus(BookingStatus.INSPECTING);
        Booking saved = bookingRepository.save(booking);
        log.info("Provider {} arrived at location for booking {}", user.getId(), bookingId);

        return bookingMapper.toBookingResponse(saved);
    }

    /**
     * Hủy đơn sửa chữa (Booking).
     * Quy tắc:
     * - Người gọi phải là Customer tạo booking hoặc Provider được phân công cho booking.
     * - Không thể hủy nếu đơn đã ở các trạng thái kết thúc: CANCELLED, WAITING_PAYMENT, COMPLETED.
     * - Khách hàng không thể hủy khi đơn đang IN_PROGRESS trừ khi đang có báo giá mới (PENDING quote) cần thương lượng.
     * - Thợ được phân công có thể hủy ở mọi trạng thái trước WAITING_PAYMENT.
     * - Khi hủy: Chuyển Booking sang CANCELLED, chuyển các Offer còn hiệu lực sang EXPIRED, chuyển các Quote PENDING sang CANCELLED.
     */
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, User user) {
        // Khóa pessimistic lock trên Booking để tránh tranh chấp cập nhật đồng thời
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Xác định vai trò của người gọi
        boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
        boolean isProvider = booking.getProvider() != null &&
                booking.getProvider().getUser() != null &&
                booking.getProvider().getUser().getId().equals(user.getId());

        // Kiểm tra quyền sở hữu: Phải là customer hoặc provider của đơn
        if (!isCustomer && !isProvider) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        // Không thể hủy nếu đơn đã hủy từ trước
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }

        // Không thể hủy nếu đơn đã tới hoặc vượt qua giai đoạn thanh toán
        if (booking.getStatus() == BookingStatus.WAITING_PAYMENT || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel booking in status: " + booking.getStatus());
        }

        // Kiểm tra điều kiện riêng của Customer: Khi IN_PROGRESS, chỉ được hủy nếu đang có báo giá PENDING
        if (isCustomer && booking.getStatus() == BookingStatus.IN_PROGRESS) {
            boolean hasPendingQuote = quoteRepository.existsByBookingIdAndStatus(bookingId, QuoteStatus.PENDING);
            if (!hasPendingQuote) {
                throw new ConflictException("Customer cannot cancel booking during IN_PROGRESS unless there is a pending quote under negotiation");
            }
        }

        // Chuyển trạng thái Booking sang CANCELLED
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.saveAndFlush(booking);

        // Hết hạn tất cả các offer đang PENDING hoặc ACCEPTED của booking này
        bookingOfferRepository.expireAllOffersForBooking(
                bookingId,
                OfferStatus.EXPIRED,
                List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED)
        );

        // Chuyển các quote đang PENDING của booking này sang CANCELLED
        List<RepairQuote> pendingQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.PENDING);
        for (RepairQuote pq : pendingQuotes) {
            pq.setStatus(QuoteStatus.CANCELLED);
            quoteRepository.save(pq);
        }

        log.info("Booking {} cancelled by user {} (role: {}). Reason: {}",
                bookingId, user.getId(), isCustomer ? "CUSTOMER" : "PROVIDER",
                (request != null ? request.reason() : "N/A"));

        return bookingMapper.toBookingResponse(saved);
    }
}
