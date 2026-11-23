package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.SendMessageRequest;
import com.nhom6.motofix.dto.respond.ChatMessageResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @PostMapping("/api/bookings/{bookingId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable UUID bookingId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal User user
    ) {
        ChatMessageResponse response = chatService.sendMessage(bookingId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/bookings/{bookingId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User user
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<ChatMessageResponse> history = chatService.getChatHistory(bookingId, user, pageable);
        return ResponseEntity.ok(history);
    }

    @MessageMapping("/bookings/{bookingId}/chat")
    public void sendStompMessage(
            @DestinationVariable UUID bookingId,
            @Valid @Payload SendMessageRequest request,
            Principal principal
    ) {
        User user = resolveUser(principal);
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated STOMP message: principal not found");
        }
        chatService.sendMessage(bookingId, request, user);
    }

    private User resolveUser(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user;
        }
        try {
            return userRepository.findById(UUID.fromString(principal.getName())).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
