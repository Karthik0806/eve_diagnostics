package com.evehealthcare.diagnostics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CentreRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 255) String location) {
}
