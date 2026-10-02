package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.Booking;
import com.evehealthcare.diagnostics.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Row-level lock (SELECT ... FOR UPDATE). Serialises payments / cancellations / webhooks per booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = "
            + "(select p.booking.id from Payment p where p.providerReference = :ref)")
    Optional<Booking> findByPaymentReferenceForUpdate(@Param("ref") String ref);

    @EntityGraph(attributePaths = {"centre", "test"})
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"centre", "test"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"centre", "test"})
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    boolean existsByUserIdAndCentreIdAndTestIdAndAppointmentTimeAndStatusIn(
            Long userId, Long centreId, Long testId, Instant appointmentTime, Collection<BookingStatus> statuses);
}
