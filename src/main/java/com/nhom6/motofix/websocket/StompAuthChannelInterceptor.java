package com.nhom6.motofix.websocket;

import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ProviderProfileRepository providerProfileRepository;

    private static final Pattern BOOKING_TOPIC_PATTERN = Pattern.compile("^/topic/bookings/([a-fA-F0-9\\-]+)$");
    private static final Pattern CUSTOMER_TOPIC_PATTERN = Pattern.compile("^/topic/customers/([a-fA-F0-9\\-]+)(/.*)?$");
    private static final Pattern PROVIDER_TOPIC_PATTERN = Pattern.compile("^/topic/providers/([a-fA-F0-9\\-]+)(/.*)?$");

    @Override
    @Transactional(readOnly = true)
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (!accessor.isMutable()) {
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            mutableAccessor.setLeaveMutable(true);
            if (StompCommand.CONNECT.equals(mutableAccessor.getCommand())) {
                handleConnect(mutableAccessor);
            } else if (StompCommand.SUBSCRIBE.equals(mutableAccessor.getCommand())) {
                handleSubscribe(mutableAccessor);
            }
            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getPasscode();
        }

        if (authHeader == null || authHeader.isBlank()) {
            throw new BadCredentialsException("Missing Authorization header in WebSocket CONNECT frame");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

        try {
            String userIdStr = jwtService.extractUserId(token);
            if (userIdStr == null) {
                throw new BadCredentialsException("Invalid JWT token: subject is missing");
            }

            UUID userId = UUID.fromString(userIdStr);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BadCredentialsException("User not found for WebSocket token"));

            if (!jwtService.isTokenValid(token, user)) {
                throw new BadCredentialsException("JWT token is invalid or expired for user " + userId);
            }

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            String currentRole = user.getCurrentRole();
            if ("CUSTOMER".equalsIgnoreCase(currentRole)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            } else if ("PROVIDER".equalsIgnoreCase(currentRole)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PROVIDER"));
            } else if (user.getRoles() != null) {
                user.getRoles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.getName())));
            }

            Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            accessor.setUser(authentication);
            log.info("WebSocket authenticated user {} with role {}", user.getId(), currentRole);

        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WebSocket authentication failed: {}", e.getMessage());
            throw new BadCredentialsException("WebSocket authentication failed: " + e.getMessage(), e);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated subscription: no Principal found in session");
        }

        User user = null;
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User u) {
            user = u;
        } else {
            try {
                user = userRepository.findById(UUID.fromString(principal.getName())).orElse(null);
            } catch (Exception ignored) {
            }
        }

        if (user == null) {
            throw new AccessDeniedException("Cannot resolve authenticated user for subscription");
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            return;
        }

        // 1. Kiểm tra quyền truy cập /topic/bookings/{bookingId}
        Matcher bookingMatcher = BOOKING_TOPIC_PATTERN.matcher(destination);
        if (bookingMatcher.matches()) {
            UUID bookingId = UUID.fromString(bookingMatcher.group(1));
            Booking booking = bookingRepository.findByIdWithCustomerAndProvider(bookingId).orElse(null);

            if (booking != null) {
                boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
                boolean isAssignedProvider = booking.getProvider() != null
                        && booking.getProvider().getUser() != null
                        && booking.getProvider().getUser().getId().equals(user.getId());

                if (!isCustomer && !isAssignedProvider) {
                    log.warn("Access denied for user {} subscribing to booking topic {} (admin is restricted from personal booking topics)", user.getId(), destination);
                    throw new AccessDeniedException("You are not authorized to subscribe to booking " + bookingId);
                }
            } else {
                log.warn("Booking not found for user {} subscribing to topic {}", user.getId(), destination);
                throw new AccessDeniedException("Booking not found for topic " + destination);
            }
            return;
        }

        // 2. Kiểm tra quyền truy cập /topic/customers/{userId}/**
        Matcher customerMatcher = CUSTOMER_TOPIC_PATTERN.matcher(destination);
        if (customerMatcher.matches()) {
            UUID targetUserId = UUID.fromString(customerMatcher.group(1));
            if (!user.getId().equals(targetUserId)) {
                log.warn("Access denied for user {} subscribing to customer topic {}", user.getId(), destination);
                throw new AccessDeniedException("You are not authorized to subscribe to customer topic " + destination);
            }
            return;
        }

        // 3. Kiểm tra quyền truy cập /topic/providers/{providerId}/**
        Matcher providerMatcher = PROVIDER_TOPIC_PATTERN.matcher(destination);
        if (providerMatcher.matches()) {
            UUID providerId = UUID.fromString(providerMatcher.group(1));
            ProviderProfile profile = providerProfileRepository.findByIdWithUser(providerId).orElse(null);

            if (profile != null) {
                boolean isOwner = profile.getUser() != null && profile.getUser().getId().equals(user.getId());
                if (!isOwner) {
                    log.warn("Access denied for user {} subscribing to provider topic {}", user.getId(), destination);
                    throw new AccessDeniedException("You are not authorized to subscribe to provider topic " + destination);
                }
            } else {
                throw new AccessDeniedException("Provider profile not found for topic " + destination);
            }
            return;
        }
    }
}
