package com.nhom6.motofix;

import com.nhom6.motofix.dto.request.CreateQuoteItemRequest;
import com.nhom6.motofix.dto.request.CreateQuoteRequest;
import com.nhom6.motofix.dto.respond.BookingResponse;
import com.nhom6.motofix.dto.respond.QuoteResponse;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.*;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BookingWorkflowTests {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private RepairQuoteRepository quoteRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private QuoteService quoteService;

    private User customer;
    private User providerUser;
    private User otherProviderUser;
    private ProviderProfile providerProfile;
    private ProviderProfile otherProviderProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setFullName("Customer Test");
        customer.setEmail("customer_" + UUID.randomUUID() + "@test.com");
        customer.setPhone("090" + System.currentTimeMillis() % 10000000);
        customer.setPasswordHash("hash");
        customer.setStatus("active");
        customer = userRepository.save(customer);

        providerUser = new User();
        providerUser.setFullName("Provider Test");
        providerUser.setEmail("provider_" + UUID.randomUUID() + "@test.com");
        providerUser.setPhone("091" + System.currentTimeMillis() % 10000000);
        providerUser.setPasswordHash("hash");
        providerUser.setStatus("active");
        providerUser = userRepository.save(providerUser);

        providerProfile = new ProviderProfile();
        providerProfile.setUser(providerUser);
        providerProfile.setBusinessName("Tiem Sua Xe A");
        providerProfile.setIdDocumentUrl("http://doc.url");
        providerProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile.setActive(true);
        providerProfile = providerProfileRepository.save(providerProfile);

        otherProviderUser = new User();
        otherProviderUser.setFullName("Other Provider");
        otherProviderUser.setEmail("other_" + UUID.randomUUID() + "@test.com");
        otherProviderUser.setPhone("092" + System.currentTimeMillis() % 10000000);
        otherProviderUser.setPasswordHash("hash");
        otherProviderUser.setStatus("active");
        otherProviderUser = userRepository.save(otherProviderUser);

        otherProviderProfile = new ProviderProfile();
        otherProviderProfile.setUser(otherProviderUser);
        otherProviderProfile.setBusinessName("Tiem Sua Xe B");
        otherProviderProfile.setIdDocumentUrl("http://doc.url");
        otherProviderProfile.setApprovalStatus(ApprovalStatus.APPROVED);
        otherProviderProfile.setActive(true);
        otherProviderProfile = providerProfileRepository.save(otherProviderProfile);

        booking = new Booking();
        booking.setCustomer(customer);
        booking.setProvider(providerProfile);
        booking.setStatus(BookingStatus.PROVIDER_SELECTED);
        booking.setProblemDescription("Xe chet may giua duong");
        booking.setLatitude(10.76);
        booking.setLongitude(106.68);
        booking = bookingRepository.save(booking);
    }

    @Test
    void testEnRoute_Success() {
        BookingResponse response = bookingService.confirmEnRoute(booking.getId(), providerUser);
        assertNotNull(response);
        assertEquals(BookingStatus.PROVIDER_EN_ROUTE, response.status());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.PROVIDER_EN_ROUTE, reloaded.getStatus());
    }

    @Test
    void testEnRoute_WrongProvider_Forbidden() {
        assertThrows(AccessDeniedException.class, () ->
                bookingService.confirmEnRoute(booking.getId(), otherProviderUser)
        );
    }

    @Test
    void testEnRoute_WrongStatus_Conflict() {
        booking.setStatus(BookingStatus.SEARCHING);
        bookingRepository.save(booking);

        assertThrows(ConflictException.class, () ->
                bookingService.confirmEnRoute(booking.getId(), providerUser)
        );
    }

    @Test
    void testArrived_Success() {
        booking.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        bookingRepository.save(booking);

        BookingResponse response = bookingService.confirmArrived(booking.getId(), providerUser);
        assertNotNull(response);
        assertEquals(BookingStatus.INSPECTING, response.status());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.INSPECTING, reloaded.getStatus());
    }

    @Test
    void testArrived_WrongProvider_Forbidden() {
        booking.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        bookingRepository.save(booking);

        assertThrows(AccessDeniedException.class, () ->
                bookingService.confirmArrived(booking.getId(), otherProviderUser)
        );
    }

    @Test
    void testArrived_WrongStatus_Conflict() {
        // Status is still PROVIDER_SELECTED, cannot arrive before en-route
        assertThrows(ConflictException.class, () ->
                bookingService.confirmArrived(booking.getId(), providerUser)
        );
    }

    @Test
    void testCreateInitialQuote_Success_CalculatesTotal() {
        booking.setStatus(BookingStatus.INSPECTING);
        bookingRepository.save(booking);

        CreateQuoteRequest request = new CreateQuoteRequest(
                "Xe hu bugi va dut day curoa",
                List.of(
                        new CreateQuoteItemRequest(QuoteItemType.LABOR, "Tien cong sua xe", 1, new BigDecimal("100000"), "Kiem tra va thay the"),
                        new CreateQuoteItemRequest(QuoteItemType.PART, "Bugi NGK", 2, new BigDecimal("80000"), "Chinh hang"),
                        new CreateQuoteItemRequest(QuoteItemType.PART, "Day curoa Bando", 1, new BigDecimal("250000"), "Chinh hang")
                )
        );

        QuoteResponse response = quoteService.createInitialQuote(booking.getId(), request, providerUser);

        assertNotNull(response);
        assertEquals(1, response.version());
        assertNull(response.basedOnQuoteId());
        assertEquals(QuoteStatus.PENDING, response.status());
        assertEquals("Xe hu bugi va dut day curoa", response.diagnosisNote());

        // Total amount: 1*100000 + 2*80000 + 1*250000 = 100000 + 160000 + 250000 = 510000
        BigDecimal expectedTotal = new BigDecimal("510000.00");
        assertEquals(0, expectedTotal.compareTo(response.totalAmount()));
        assertEquals(3, response.items().size());

        // Verify database persistence
        RepairQuote savedQuote = quoteRepository.findById(response.id()).orElseThrow();
        assertEquals(3, savedQuote.getItems().size());
        assertEquals(0, expectedTotal.compareTo(savedQuote.getTotalAmount()));
    }

    @Test
    void testCreateInitialQuote_DuplicateVersion1_Conflict() {
        booking.setStatus(BookingStatus.INSPECTING);
        bookingRepository.save(booking);

        CreateQuoteRequest request = new CreateQuoteRequest(
                "Lan 1",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, new BigDecimal("50000"), null))
        );
        quoteService.createInitialQuote(booking.getId(), request, providerUser);

        // Try creating v1 again
        assertThrows(ConflictException.class, () ->
                quoteService.createInitialQuote(booking.getId(), request, providerUser)
        );
    }

    @Test
    void testCreateInitialQuote_WrongStatus_Conflict() {
        // Booking is still in PROVIDER_EN_ROUTE
        booking.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        bookingRepository.save(booking);

        CreateQuoteRequest request = new CreateQuoteRequest(
                "Chua den noi",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, new BigDecimal("50000"), null))
        );

        assertThrows(ConflictException.class, () ->
                quoteService.createInitialQuote(booking.getId(), request, providerUser)
        );
    }

    @Test
    void testCreateInitialQuote_WrongProvider_Forbidden() {
        booking.setStatus(BookingStatus.INSPECTING);
        bookingRepository.save(booking);

        CreateQuoteRequest request = new CreateQuoteRequest(
                "Nguoi khac tao",
                List.of(new CreateQuoteItemRequest(QuoteItemType.LABOR, "Cong", 1, new BigDecimal("50000"), null))
        );

        assertThrows(AccessDeniedException.class, () ->
                quoteService.createInitialQuote(booking.getId(), request, otherProviderUser)
        );
    }
}

