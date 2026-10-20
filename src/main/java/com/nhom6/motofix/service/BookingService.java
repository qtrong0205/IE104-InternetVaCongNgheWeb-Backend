package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.entity.BookingMedia;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.BookingMapper;
import com.nhom6.motofix.repository.BookingMediaRepository;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingMapper bookingMapper;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var booking = bookingMapper.toBooking(request);
        booking.setCustomer(user);

        if (request.media() != null) {
            request.media().forEach(item -> {
                var media = new BookingMedia();
                media.setUrl(item.url());
                media.setType(item.type());
                booking.addMedia(media);
            });
        }

        var saved = bookingRepository.save(booking);
        return bookingMapper.toBookingResponse(saved);
    }
}
