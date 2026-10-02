package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.Payment;
import com.evehealthcare.diagnostics.domain.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderReference(String providerReference);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    @EntityGraph(attributePaths = {"booking"})
    Optional<Payment> findByIdAndBookingUserId(Long id, Long userId);
}
