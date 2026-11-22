package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.respond.IncomingOfferResponse;
import com.nhom6.motofix.dto.respond.SyncEventPayload;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.BookingOffer;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.SyncEventType;
import com.nhom6.motofix.websocket.BookingSyncApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSyncService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Thông báo cho thợ khi có offer cứu hộ mới trong vùng quét.
     * Destination: /topic/providers/{providerId}/offers
     */
    public void notifyOfferNew(UUID providerId, IncomingOfferResponse offer) {
        if (providerId == null || offer == null) return;
        String dest = "/topic/providers/" + providerId + "/offers";
        SyncEventPayload payload = SyncEventPayload.of(
                SyncEventType.OFFER_NEW,
                offer.bookingId(),
                null,
                null,
                null,
                "Có yêu cầu cứu hộ mới gần bạn",
                offer,
                "/api/providers/incoming-offers/" + offer.offerId()
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }

    /**
     * Thông báo cho khách hàng khi thợ đồng ý nhận offer và gửi báo giá sơ bộ.
     * Destination: /topic/bookings/{bookingId}
     */
    public void notifyOfferAccepted(Booking booking, BookingOffer offer, User providerUser) {
        if (booking == null || offer == null) return;
        String dest = "/topic/bookings/" + booking.getId();
        SyncEventPayload payload = SyncEventPayload.of(
                SyncEventType.OFFER_ACCEPTED,
                booking.getId(),
                offer.getId(),
                booking.getStatus(),
                null,
                providerUser != null ? providerUser.getId() : null,
                "Thợ đã phản hồi đồng ý cứu hộ",
                offer.getId(),
                "/api/bookings/" + booking.getId() + "/offers"
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }

    /**
     * Thông báo cho thợ khi offer của mình hết hạn (hoặc thợ khác đã được chọn / thợ tắt online / logout).
     * Destination: /topic/providers/{providerId}/offers
     */
    public void notifyOfferExpired(UUID providerId, UUID offerId, UUID bookingId) {
        if (providerId == null) return;
        String dest = "/topic/providers/" + providerId + "/offers";
        SyncEventPayload payload = SyncEventPayload.of(
                SyncEventType.OFFER_EXPIRED,
                bookingId,
                offerId,
                null,
                null,
                null,
                "Offer đã hết hiệu lực do khách đã chọn thợ khác, quá hạn hoặc thợ không còn online",
                offerId,
                "/api/providers/incoming-offers"
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }

    /**
     * Thông báo cho khách hàng khi một offer hết hiệu lực hoặc thợ không còn online / logout.
     * Destination: /topic/bookings/{bookingId}
     */
    public void notifyOfferExpiredForBooking(UUID bookingId, UUID offerId) {
        if (bookingId == null || offerId == null) return;
        String dest = "/topic/bookings/" + bookingId;
        SyncEventPayload payload = SyncEventPayload.of(
                SyncEventType.OFFER_EXPIRED,
                bookingId,
                offerId,
                com.nhom6.motofix.enums.BookingStatus.SEARCHING,
                null,
                null,
                "Một thợ không còn online hoặc offer đã hết hiệu lực",
                offerId,
                "/api/bookings/" + bookingId + "/offers"
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }

    /**
     * Thông báo khi khách hàng chọn thợ:
     * - Gửi tới /topic/bookings/{bookingId} (cho khách và thợ chính theo dõi)
     * - Gửi tới /topic/providers/{selectedProviderId}/offers (cho thợ được chọn biết kết quả qua kênh đã nghe trước khi được chọn)
     */
    public void notifyProviderSelected(Booking booking, UUID selectedProviderId, UUID selectedOfferId, User customer) {
        if (booking == null) return;
        String bookingDest = "/topic/bookings/" + booking.getId();
        SyncEventPayload payload = SyncEventPayload.of(
                SyncEventType.PROVIDER_SELECTED,
                booking.getId(),
                selectedOfferId,
                booking.getStatus(),
                null,
                customer != null ? customer.getId() : null,
                "Khách hàng đã chọn bạn làm thợ cứu hộ cho đơn này",
                selectedProviderId,
                "/api/bookings/" + booking.getId()
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(bookingDest, payload));

        if (selectedProviderId != null) {
            String provDest = "/topic/providers/" + selectedProviderId + "/offers";
            eventPublisher.publishEvent(new BookingSyncApplicationEvent(provDest, payload));
        }
    }

    public void notifyProviderSelected(Booking booking, UUID selectedProviderId, User customer) {
        notifyProviderSelected(booking, selectedProviderId, null, customer);
    }

    /**
     * Thông báo thay đổi trạng thái tiến trình di chuyển, hoàn thành, hủy đơn.
     * Destination: /topic/bookings/{bookingId}
     */
    public void notifyBookingStatusChanged(Booking booking, SyncEventType eventType, User actor, String message, Object data) {
        if (booking == null) return;
        String dest = "/topic/bookings/" + booking.getId();
        SyncEventPayload payload = SyncEventPayload.of(
                eventType,
                booking.getId(),
                booking.getStatus(),
                null,
                actor != null ? actor.getId() : null,
                message,
                data,
                "/api/bookings/" + booking.getId()
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }

    /**
     * Thông báo các sự kiện báo giá: QUOTE_NEW, QUOTE_REVISED, QUOTE_ACCEPTED, QUOTE_ROLLBACK.
     * Destination: /topic/bookings/{bookingId}
     */
    public void notifyQuoteEvent(Booking booking, SyncEventType eventType, Integer quoteVersion, User actor, String message, Object quoteData) {
        if (booking == null) return;
        String dest = "/topic/bookings/" + booking.getId();
        SyncEventPayload payload = SyncEventPayload.of(
                eventType,
                booking.getId(),
                booking.getStatus(),
                quoteVersion,
                actor != null ? actor.getId() : null,
                message,
                quoteData,
                "/api/bookings/" + booking.getId() + "/quotes"
        );
        eventPublisher.publishEvent(new BookingSyncApplicationEvent(dest, payload));
    }
}
