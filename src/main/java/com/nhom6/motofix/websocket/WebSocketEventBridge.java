package com.nhom6.motofix.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventBridge {

    private final SimpMessagingTemplate messagingTemplate;
    private final List<BookingSyncApplicationEvent> capturedEvents = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSyncEvent(BookingSyncApplicationEvent event) {
        if (event == null || event.destination() == null || event.payload() == null) {
            return;
        }
        log.info("WebSocket publish [AFTER_COMMIT]: destination={}, eventType={}, bookingId={}",
                event.destination(), event.payload().eventType(), event.payload().bookingId());

        capturedEvents.add(event);
        messagingTemplate.convertAndSend(event.destination(), event.payload());
    }

    public List<BookingSyncApplicationEvent> getCapturedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(capturedEvents));
    }

    public void clearCapturedEvents() {
        capturedEvents.clear();
    }
}

