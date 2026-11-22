package com.nhom6.motofix.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);
}
