package com.nhom6.motofix.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ConflictException extends RuntimeException {
    private final UUID bookingId;
    private final String bookingStatus;

    public ConflictException(String message) {
        super(message);
        this.bookingId = null;
        this.bookingStatus = null;
    }

    public ConflictException(String message, UUID bookingId, String bookingStatus) {
        super(message);
        this.bookingId = bookingId;
        this.bookingStatus = bookingStatus;
    }
}
