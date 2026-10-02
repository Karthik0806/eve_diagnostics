package com.evehealthcare.diagnostics.dto;

import java.util.List;

public record CentreResponse(Long id, String name, String location, List<OfferingResponse> tests) {
}
