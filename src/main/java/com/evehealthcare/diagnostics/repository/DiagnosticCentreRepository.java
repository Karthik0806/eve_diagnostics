package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.DiagnosticCentre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticCentreRepository extends JpaRepository<DiagnosticCentre, Long> {
    boolean existsByNameIgnoreCaseAndLocationIgnoreCase(String name, String location);

    Page<DiagnosticCentre> findByLocationContainingIgnoreCase(String location, Pageable pageable);

    /** {@code pattern} must already be a lower-cased LIKE pattern, e.g. "%hyderabad%". */
    @Query("select c from DiagnosticCentre c where lower(c.location) like :pattern "
            + "and exists (select 1 from CentreTest ct where ct.centre = c and ct.test.id = :testId)")
    Page<DiagnosticCentre> searchByLocationAndTest(@Param("pattern") String pattern,
                                                   @Param("testId") Long testId,
                                                   Pageable pageable);
}
