package com.nhom6.motofix.config;

import com.nhom6.motofix.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket thuần
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // Endpoint hỗ trợ SockJS fallback khi client không hỗ trợ native WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Tiền tố cho các kênh phân phối sự kiện đồng bộ
        registry.enableSimpleBroker("/topic", "/queue");

        // Tiền tố cho các message gửi từ client lên ứng dụng (nếu có)
        registry.setApplicationDestinationPrefixes("/app");

        // Tiền tố cho tin nhắn hướng người dùng cá nhân
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Đăng ký interceptor xác thực JWT và kiểm tra phân quyền đăng ký kênh (subscribe)
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
