package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.domain.Booking;
import com.evehealthcare.diagnostics.domain.BookingStatus;
import com.evehealthcare.diagnostics.domain.CentreTest;
import com.evehealthcare.diagnostics.dto.BookingRequest;
import com.evehealthcare.diagnostics.dto.BookingResponse;
import com.evehealthcare.diagnostics.dto.PageResponse;
import com.evehealthcare.diagnostics.repository.BookingRepository;
import com.evehealthcare.diagnostics.repository.CentreTestRepository;
import com.evehealthcare.diagnostics.repository.DiagnosticCentreRepository;
import com.evehealthcare.diagnostics.repository.DiagnosticTestRepository;
import com.evehealthcare.diagnostics.repository.UserRepository;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookings;
    private final CentreTestRepository offerings;
    private final DiagnosticCentreRepository centres;
    private final DiagnosticTestRepository tests;
    private final UserRepository users;

    @Transactional
    public BookingResponse create(AuthUser user, BookingRequest req) {
        if (!centres.existsById(req.centreId())) {
            throw ApiException.notFound("Centre " + req.centreId() + " not found");
        }
        if (!tests.existsById(req.testId())) {
            throw ApiException.notFound("Test " + req.testId() + " not found");
        }
        CentreTest offering = offerings.findByCentreIdAndTestId(req.centreId(), req.testId())
                .orElseThrow(() -> ApiException.notFound("This centre does not offer the requested test"));

        if (bookings.existsByUserIdAndCentreIdAndTestIdAndAppointmentTimeAndStatusIn(user.id(), req.centreId(),
                req.testId(), req.appointmentTime(), EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
            throw ApiException.conflict("You already have an active booking for this test, centre and time");
        }

        Booking b = new Booking();
        b.setUser(users.getReferenceById(user.id()));
        b.setCentre(offering.getCentre());
        b.setTest(offering.getTest());
        b.setAppointmentTime(req.appointmentTime());
        b.setAmount(offering.getPrice());            // price snapshot
        b.setStatus(BookingStatus.PENDING);
        Booking saved = bookings.saveAndFlush(b);
        log.info("Booking created bookingId={} userId={} amount={}", saved.getId(), user.id(), saved.getAmount());
        return BookingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(AuthUser user, BookingStatus status, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Booking> result = (status == null)
                ? bookings.findByUserId(user.id(), pageable)
                : bookings.findByUserIdAndStatus(user.id(), status, pageable);
        return PageResponse.of(result, BookingResponse::from);
    }

    /** Someone else's booking is reported as 404 (not 403) so booking ids cannot be probed. */
    @Transactional(readOnly = true)
    public BookingResponse get(AuthUser user, Long id) {
        return bookings.findByIdAndUserId(id, user.id())
                .map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound("Booking " + id + " not found"));
    }

    @Transactional
    public BookingResponse cancel(AuthUser user, Long id) {
        Booking b = bookings.findByIdForUpdate(id)
                .filter(x -> x.getUser().getId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Booking " + id + " not found"));
        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("Booking is already cancelled");
        }
        if (!b.getAppointmentTime().isAfter(Instant.now())) {
            throw ApiException.conflict("Bookings whose appointment time has passed cannot be cancelled");
        }
        b.setStatus(BookingStatus.CANCELLED);
        log.info("Booking cancelled bookingId={} userId={}", id, user.id());
        return BookingResponse.from(bookings.saveAndFlush(b));
    }
}
