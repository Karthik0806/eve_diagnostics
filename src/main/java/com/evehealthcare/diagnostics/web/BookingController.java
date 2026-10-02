package com.evehealthcare.diagnostics.web;

import com.evehealthcare.diagnostics.domain.BookingStatus;
import com.evehealthcare.diagnostics.dto.BookingRequest;
import com.evehealthcare.diagnostics.dto.BookingResponse;
import com.evehealthcare.diagnostics.dto.PageResponse;
import com.evehealthcare.diagnostics.security.AuthUser;
import com.evehealthcare.diagnostics.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping({"", "/"})
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody BookingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(user, req));
    }

    @GetMapping({"", "/"})
    public PageResponse<BookingResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) BookingStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return bookingService.list(user, status, page, size);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.get(user, id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return bookingService.cancel(user, id);
    }
}
