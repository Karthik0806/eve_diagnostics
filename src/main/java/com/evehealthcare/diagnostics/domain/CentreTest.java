package com.evehealthcare.diagnostics.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "centre_tests", uniqueConstraints = @UniqueConstraint(columnNames = {"centre_id", "test_id"}))
@Getter @Setter @NoArgsConstructor
public class CentreTest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "centre_id", nullable = false)
    private DiagnosticCentre centre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private DiagnosticTest test;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
