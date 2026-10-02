package com.evehealthcare.diagnostics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestRequest(@NotBlank @Size(max = 150) String name, @Size(max = 500) String description) {
}
