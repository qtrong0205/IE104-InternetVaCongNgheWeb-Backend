package com.nhom6.motofix.dto.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.SyncEventType;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncEventPayload(
        UUID eventId,
        SyncEventType eventType,
        UUID bookingId,
        BookingStatus status,
        Integer statusOrder,
        Integer quoteVersion,
        LocalDateTime timestamp,
        UUID actorId,
        String message,
        Object data,
        String snapshotUrl
) {
    public static int calculateStatusOrder(BookingStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case SEARCHING -> 1;
            case SELECTED_PROVIDER, PROVIDER_SELECTED -> 2;
            case PROVIDER_EN_ROUTE -> 3;
            case INSPECTING, AWAITING_QUOTE_APPROVAL -> 4;
            case IN_PROGRESS -> 5;
            case WAITING_PAYMENT -> 6;
            case COMPLETED -> 7;
            case CANCELLED -> 8;
        };
    }

    public static SyncEventPayload of(
            SyncEventType eventType,
            UUID bookingId,
            BookingStatus status,
            Integer quoteVersion,
            UUID actorId,
            String message,
            Object data,
            String snapshotUrl
    ) {
        return new SyncEventPayload(
                UUID.randomUUID(),
                eventType,
                bookingId,
                status,
                calculateStatusOrder(status),
                quoteVersion,
                LocalDateTime.now(),
                actorId,
                message,
                data,
                snapshotUrl
        );
    }
}
