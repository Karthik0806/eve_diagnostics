package com.evehealthcare.diagnostics.dto;

import com.evehealthcare.diagnostics.domain.DiagnosticTest;

public record TestResponse(Long id, String name, String description) {
    public static TestResponse from(DiagnosticTest t) {
        return new TestResponse(t.getId(), t.getName(), t.getDescription());
    }
}
