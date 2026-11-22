package com.nhom6.motofix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MockEmailService implements EmailService {
    private final Map<String, String> latestTokens = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String normalizedEmail = toEmail != null ? toEmail.trim().toLowerCase() : "";
        latestTokens.put(normalizedEmail, resetToken);
        log.info("[TEST-DOUBLE EMAIL SERVICE] Password reset email queued for {} with token {}", normalizedEmail, resetToken);
    }

    public String getLatestToken(String email) {
        if (email == null) return null;
        return latestTokens.get(email.trim().toLowerCase());
    }

    public void clear() {
        latestTokens.clear();
    }
}
