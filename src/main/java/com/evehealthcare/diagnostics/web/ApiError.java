package com.evehealthcare.diagnostics.web;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldIssue> details) {
    public record FieldIssue(String field, String message) {}
}
