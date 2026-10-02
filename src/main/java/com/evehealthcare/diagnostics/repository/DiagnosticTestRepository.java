package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.DiagnosticTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticTestRepository extends JpaRepository<DiagnosticTest, Long> {
    boolean existsByNameIgnoreCase(String name);
    Page<DiagnosticTest> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
