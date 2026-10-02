package com.evehealthcare.diagnostics.web;

import com.evehealthcare.diagnostics.dto.PageResponse;
import com.evehealthcare.diagnostics.dto.TestRequest;
import com.evehealthcare.diagnostics.dto.TestResponse;
import com.evehealthcare.diagnostics.service.CatalogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Diagnostic tests")
@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
public class TestController {
    private final CatalogService catalog;

    @GetMapping({"", "/"})
    public PageResponse<TestResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return catalog.listTests(q, page, size);
    }

    @GetMapping("/{id}")
    public TestResponse get(@PathVariable Long id) {
        return catalog.getTest(id);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping({"", "/"})
    public ResponseEntity<TestResponse> create(@Valid @RequestBody TestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createTest(req));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public TestResponse update(@PathVariable Long id, @Valid @RequestBody TestRequest req) {
        return catalog.updateTest(id, req);
    }
}
