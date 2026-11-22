package com.nhom6.motofix.websocket;

import com.nhom6.motofix.dto.respond.SyncEventPayload;

public record BookingSyncApplicationEvent(
        String destination,
        SyncEventPayload payload
) {
}
