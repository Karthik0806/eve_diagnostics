package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.CentreTest;

import java.math.BigDecimal;

public record OfferingResponse(Long testId, String testName, BigDecimal price) {
    public static OfferingResponse from(CentreTest ct) {
        return new OfferingResponse(ct.getTest().getId(), ct.getTest().getName(), ct.getPrice());
    }
}
