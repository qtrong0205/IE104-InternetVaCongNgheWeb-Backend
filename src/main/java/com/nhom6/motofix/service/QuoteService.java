package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateQuoteItemRequest;
import com.nhom6.motofix.dto.request.CreateQuoteRequest;
import com.nhom6.motofix.dto.respond.QuoteResponse;
import com.nhom6.motofix.entity.Booking;
import com.nhom6.motofix.entity.RepairQuote;
import com.nhom6.motofix.entity.RepairQuoteItem;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.QuoteItemStatus;
import com.nhom6.motofix.enums.QuoteStatus;
import com.nhom6.motofix.exception.ConflictException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.repository.BookingRepository;
import com.nhom6.motofix.repository.RepairQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteService {
    private final BookingRepository bookingRepository;
    private final RepairQuoteRepository quoteRepository;

    /**
     * Provider tạo báo giá chẩn đoán phiên bản đầu tiên (version 1).
     * Yêu cầu: Provider được giao đơn, Booking ở trạng thái INSPECTING, chưa có quote v1.
     */
    @Transactional
    public QuoteResponse createInitialQuote(UUID bookingId, CreateQuoteRequest request, User user) {
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền sở hữu: Chỉ provider được chọn của booking mới được thao tác
        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        // Kiểm tra trạng thái: Phải ở trạng thái INSPECTING
        if (booking.getStatus() != BookingStatus.INSPECTING) {
            throw new ConflictException("Booking must be in INSPECTING status to create initial quote, current status is " + booking.getStatus());
        }

        // Không cho phép tạo nhiều chẩn đoán phiên bản 1 cho cùng một booking
        if (quoteRepository.existsByBookingIdAndVersion(bookingId, 1)) {
            throw new ConflictException("Initial quote (version 1) already exists for this booking");
        }

        RepairQuote quote = new RepairQuote();
        quote.setBooking(booking);
        quote.setVersion(1);
        quote.setBasedOnQuote(null);
        quote.setDiagnosisNote(request.diagnosisNote());
        quote.setStatus(QuoteStatus.PENDING);
        quote.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateQuoteItemRequest itemReq : request.items()) {
            RepairQuoteItem item = new RepairQuoteItem();
            item.setItemType(itemReq.itemType());
            item.setName(itemReq.name());
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(itemReq.unitPrice());
            item.setDescription(itemReq.description());

            BigDecimal itemAmount = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            item.setAmount(itemAmount);
            item.setStatus(QuoteItemStatus.PENDING);
            item.setCreatedAt(LocalDateTime.now());

            quote.addItem(item);
            totalAmount = totalAmount.add(itemAmount);
        }
        quote.setTotalAmount(totalAmount);

        RepairQuote saved = quoteRepository.save(quote);
        log.info("Provider {} created quote v1 for booking {}, total amount: {}", user.getId(), bookingId, totalAmount);

        return QuoteResponse.from(saved);
    }

    /**
     * Customer chấp nhận một chẩn đoán / báo giá.
     * Endpoint: POST /api/bookings/{id}/quotes/{quoteId}/accept
     * Quy tắc:
     * - Chỉ Customer sở hữu booking mới được chấp nhận.
     * - Booking phải ở trạng thái INSPECTING hoặc IN_PROGRESS.
     * - Kiểm tra dữ liệu bất thường: nếu có > 1 quote PENDING hoặc > 1 quote ACCEPTED -> ném ConflictException.
     * - Quote phải thuộc về booking và đang ở trạng thái PENDING.
     * - Báo giá cũ đã ACCEPTED (nếu có) sẽ được chuyển sang SUPERSEDED.
     * - Quote được chấp nhận chuyển sang ACCEPTED, các item chuyển sang ACCEPTED.
     * - Nếu Booking đang ở INSPECTING, chuyển sang IN_PROGRESS.
     */
    @Transactional
    public QuoteResponse acceptQuote(UUID bookingId, UUID quoteId, User user) {
        // Khóa pessimistic lock trên Booking để tránh xung đột đồng thời
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền sở hữu: Phải là khách hàng tạo booking
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the customer of this booking");
        }

        // Kiểm tra trạng thái Booking: Phải là INSPECTING hoặc IN_PROGRESS
        if (booking.getStatus() != BookingStatus.INSPECTING && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ConflictException("Booking status must be INSPECTING or IN_PROGRESS to accept quote, but was: " + booking.getStatus());
        }

        // Kiểm tra tính bất thường của dữ liệu trong database
        List<RepairQuote> pendingQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.PENDING);
        if (pendingQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple PENDING quotes found for booking " + bookingId);
        }

        List<RepairQuote> acceptedQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.ACCEPTED);
        if (acceptedQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple ACCEPTED quotes found for booking " + bookingId);
        }

        // Kiểm tra sự tồn tại của Quote
        RepairQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        // Kiểm tra Quote có thuộc về Booking không
        if (!quote.getBooking().getId().equals(bookingId)) {
            throw new ConflictException("Quote does not belong to this booking");
        }

        // Chỉ chấp nhận quote đang ở trạng thái PENDING
        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new ConflictException("Only PENDING quotes can be accepted. Current status: " + quote.getStatus());
        }

        // Nếu trước đó đã có quote ACCEPTED, chuyển quote đó sang SUPERSEDED
        for (RepairQuote prevAccepted : acceptedQuotes) {
            if (!prevAccepted.getId().equals(quoteId)) {
                prevAccepted.setStatus(QuoteStatus.SUPERSEDED);
                quoteRepository.save(prevAccepted);
                log.info("Superseded previous accepted quote {} for booking {}", prevAccepted.getId(), bookingId);
            }
        }

        // Cập nhật trạng thái quote sang ACCEPTED
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setRespondedAt(LocalDateTime.now());

        // Cập nhật tất cả các item trong quote sang ACCEPTED
        if (quote.getItems() != null) {
            for (RepairQuoteItem item : quote.getItems()) {
                item.setStatus(QuoteItemStatus.ACCEPTED);
            }
        }

        // Nếu booking đang ở INSPECTING thì chuyển sang IN_PROGRESS
        if (booking.getStatus() == BookingStatus.INSPECTING) {
            booking.setStatus(BookingStatus.IN_PROGRESS);
            bookingRepository.save(booking);
            log.info("Booking {} transitioned from INSPECTING to IN_PROGRESS upon quote acceptance", bookingId);
        }

        RepairQuote savedQuote = quoteRepository.save(quote);
        log.info("Customer {} accepted quote {} for booking {}", user.getId(), quoteId, bookingId);

        return QuoteResponse.from(savedQuote);
    }

    /**
     * Provider chỉnh sửa lại chẩn đoán / báo giá (tạo version mới).
     * Endpoint: POST /api/bookings/{id}/quotes/revise
     * Quy tắc:
     * - Không truyền quoteId: backend tự xác định quote cần xử lý.
     * - Chỉ Provider được phân công cho booking mới được thao tác.
     * - Booking phải ở trạng thái INSPECTING hoặc IN_PROGRESS.
     * - Kiểm tra dữ liệu bất thường: nếu có > 1 quote PENDING hoặc > 1 quote ACCEPTED -> ném ConflictException.
     * - Nếu không có quote PENDING và cũng không có quote ACCEPTED -> ném ConflictException (không có quote hợp lệ).
     * - Trường hợp 1: Booking chỉ có PENDING -> quote PENDING cũ thành CANCELLED, tạo quote mới PENDING với based_on_quote_id = null.
     * - Trường hợp 2: Booking chỉ có ACCEPTED -> giữ nguyên ACCEPTED, tạo quote mới PENDING với based_on_quote_id = ID của ACCEPTED.
     * - Trường hợp 3: Booking có cả ACCEPTED và PENDING -> quote PENDING cũ thành CANCELLED, giữ nguyên ACCEPTED,
     *                 tạo quote mới PENDING với based_on_quote_id = ID của ACCEPTED (tuyệt đối không trỏ vào PENDING cũ).
     */
    @Transactional
    public QuoteResponse reviseQuote(UUID bookingId, CreateQuoteRequest request, User user) {
        // Khóa pessimistic lock trên Booking để tránh xung đột đồng thời
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền sở hữu: Chỉ provider được chọn của booking mới được thao tác
        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        // Kiểm tra trạng thái Booking: Phải là INSPECTING hoặc IN_PROGRESS
        if (booking.getStatus() != BookingStatus.INSPECTING && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ConflictException("Booking status must be INSPECTING or IN_PROGRESS to revise quote, but was: " + booking.getStatus());
        }

        // Kiểm tra tính bất thường của dữ liệu
        List<RepairQuote> pendingQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.PENDING);
        if (pendingQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple PENDING quotes found for booking " + bookingId);
        }

        List<RepairQuote> acceptedQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.ACCEPTED);
        if (acceptedQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple ACCEPTED quotes found for booking " + bookingId);
        }

        RepairQuote pendingQuote = pendingQuotes.isEmpty() ? null : pendingQuotes.get(0);
        RepairQuote acceptedQuote = acceptedQuotes.isEmpty() ? null : acceptedQuotes.get(0);

        // Ràng buộc 6: Không cho phép revise nếu không có quote PENDING hoặc ACCEPTED hợp lệ
        if (pendingQuote == null && acceptedQuote == null) {
            throw new ConflictException("Cannot revise quote: Booking does not have any active PENDING or ACCEPTED quote to revise");
        }

        // Nếu có quote PENDING hiện tại, hủy quote PENDING đó (chuyển sang CANCELLED)
        if (pendingQuote != null) {
            pendingQuote.setStatus(QuoteStatus.CANCELLED);
            quoteRepository.save(pendingQuote);
            log.info("Cancelled unapproved pending quote {} for booking {}", pendingQuote.getId(), bookingId);
        }

        // based_on_quote_id chỉ được trỏ đến quote ACCEPTED (null nếu chưa từng có quote ACCEPTED)
        RepairQuote basedQuote = acceptedQuote;

        // Tính version mới tiếp theo
        int nextVersion = quoteRepository.findMaxVersionByBookingId(bookingId) + 1;

        RepairQuote revisedQuote = new RepairQuote();
        revisedQuote.setBooking(booking);
        revisedQuote.setVersion(nextVersion);
        revisedQuote.setBasedOnQuote(basedQuote);
        revisedQuote.setDiagnosisNote(request.diagnosisNote());
        revisedQuote.setStatus(QuoteStatus.PENDING);
        revisedQuote.setCreatedAt(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateQuoteItemRequest itemReq : request.items()) {
            RepairQuoteItem item = new RepairQuoteItem();
            item.setItemType(itemReq.itemType());
            item.setName(itemReq.name());
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(itemReq.unitPrice());
            item.setDescription(itemReq.description());

            BigDecimal itemAmount = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            item.setAmount(itemAmount);
            item.setStatus(QuoteItemStatus.PENDING);
            item.setCreatedAt(LocalDateTime.now());

            revisedQuote.addItem(item);
            totalAmount = totalAmount.add(itemAmount);
        }
        revisedQuote.setTotalAmount(totalAmount);

        RepairQuote savedQuote = quoteRepository.save(revisedQuote);
        log.info("Provider {} revised quote (new v{}) for booking {}, based on quote {}",
                user.getId(), nextVersion, bookingId, (basedQuote != null ? basedQuote.getId() : "null"));

        return QuoteResponse.from(savedQuote);
    }

    /**
     * Provider thực hiện rollback: Hủy phiên bản báo giá PENDING đang chờ duyệt
     * và quay lại sử dụng phiên bản ACCEPTED gần nhất.
     * Endpoint: POST /api/bookings/{id}/quotes/rollback
     * Điều kiện:
     * - Chỉ PROVIDER được phân công của booking mới được thao tác.
     * - Booking phải ở trạng thái IN_PROGRESS.
     * - Booking phải có đúng 1 quote PENDING và đúng 1 quote ACCEPTED.
     * - Quote PENDING chuyển thành CANCELLED.
     * - Quote ACCEPTED giữ nguyên, trả về QuoteResponse của phiên bản ACCEPTED này.
     */
    @Transactional
    public QuoteResponse rollbackQuote(UUID bookingId, User user) {
        // Khóa bi quan (pessimistic lock) trên Booking để tránh xung đột đồng thời với revise hoặc accept
        Booking booking = bookingRepository.findByIdWithLock(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Kiểm tra quyền sở hữu: Chỉ provider được phân công của booking mới được thao tác
        if (booking.getProvider() == null ||
                booking.getProvider().getUser() == null ||
                !booking.getProvider().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the assigned provider for this booking");
        }

        // Kiểm tra trạng thái Booking: Bắt buộc phải là IN_PROGRESS
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ConflictException("Booking status must be IN_PROGRESS to rollback quote, but was: " + booking.getStatus());
        }

        // Kiểm tra tính bất thường và số lượng quote PENDING
        List<RepairQuote> pendingQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.PENDING);
        if (pendingQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple PENDING quotes found for booking " + bookingId);
        }
        if (pendingQuotes.isEmpty()) {
            throw new ConflictException("No PENDING quote found to rollback for booking " + bookingId);
        }

        // Kiểm tra tính bất thường và số lượng quote ACCEPTED
        List<RepairQuote> acceptedQuotes = quoteRepository.findByBookingIdAndStatus(bookingId, QuoteStatus.ACCEPTED);
        if (acceptedQuotes.size() > 1) {
            throw new ConflictException("Abnormal quote state: multiple ACCEPTED quotes found for booking " + bookingId);
        }
        if (acceptedQuotes.isEmpty()) {
            throw new ConflictException("No ACCEPTED quote found to revert to for booking " + bookingId);
        }

        RepairQuote pendingQuote = pendingQuotes.get(0);
        RepairQuote acceptedQuote = acceptedQuotes.get(0);

        // Chuyển quote PENDING hiện tại thành CANCELLED
        pendingQuote.setStatus(QuoteStatus.CANCELLED);
        quoteRepository.save(pendingQuote);

        log.info("Provider {} rolled back quote {} (v{}) for booking {}, reverted to active accepted quote {} (v{})",
                user.getId(), pendingQuote.getId(), pendingQuote.getVersion(),
                bookingId, acceptedQuote.getId(), acceptedQuote.getVersion());

        // Trả về DTO của phiên bản ACCEPTED tiếp tục có hiệu lực
        return QuoteResponse.from(acceptedQuote);
    }

    /**
     * Lấy danh sách các phiên bản chẩn đoán của booking (theo thứ tự version tăng dần).
     * Endpoint: GET /api/bookings/{id}/quotes
     */
    @Transactional(readOnly = true)
    public List<QuoteResponse> getQuotesForBooking(UUID bookingId, User user) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
        boolean isProvider = booking.getProvider() != null &&
                booking.getProvider().getUser() != null &&
                booking.getProvider().getUser().getId().equals(user.getId());

        if (!isCustomer && !isProvider) {
            throw new AccessDeniedException("You do not have permission to view quotes for this booking");
        }

        return quoteRepository.findByBookingIdOrderByVersionAsc(bookingId)
                .stream()
                .map(QuoteResponse::from)
                .toList();
    }

    /**
     * Lấy chi tiết một phiên bản chẩn đoán.
     * Endpoint: GET /api/bookings/{id}/quotes/{quoteId}
     */
    @Transactional(readOnly = true)
    public QuoteResponse getQuoteDetails(UUID bookingId, UUID quoteId, User user) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(user.getId());
        boolean isProvider = booking.getProvider() != null &&
                booking.getProvider().getUser() != null &&
                booking.getProvider().getUser().getId().equals(user.getId());

        if (!isCustomer && !isProvider) {
            throw new AccessDeniedException("You do not have permission to view quotes for this booking");
        }

        RepairQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (!quote.getBooking().getId().equals(bookingId)) {
            throw new ConflictException("Quote does not belong to this booking");
        }

        return QuoteResponse.from(quote);
    }
}

