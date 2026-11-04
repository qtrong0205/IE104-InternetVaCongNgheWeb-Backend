package com.nhom6.motofix.job;

import com.nhom6.motofix.enums.OfferStatus;
import com.nhom6.motofix.repository.BookingOfferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfferExpirationJob {
    private final BookingOfferRepository bookingOfferRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirePendingOffers() {
        int n = bookingOfferRepository.expireOffers(
                OfferStatus.PENDING, OfferStatus.EXPIRED, LocalDateTime.now());
        if (n > 0) log.info("Expired {} pending offers", n);
    }
}
