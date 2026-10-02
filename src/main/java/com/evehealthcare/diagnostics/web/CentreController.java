package com.evehealthcare.diagnostics.web;

import com.evehealthcare.diagnostics.dto.*;
import com.evehealthcare.diagnostics.service.CatalogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Diagnostic centres")
@RestController
@RequestMapping("/centres")
@RequiredArgsConstructor
public class CentreController {
    private final CatalogService catalog;

    @GetMapping({"", "/"})
    public PageResponse<CentreResponse> list(@RequestParam(required = false) String location,
                                             @RequestParam(required = false) Long testId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return catalog.listCentres(location, testId, page, size);
    }

    @GetMapping("/{id}")
    public CentreResponse get(@PathVariable Long id) {
        return catalog.getCentre(id);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping({"", "/"})
    public ResponseEntity<CentreResponse> create(@Valid @RequestBody CentreRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createCentre(req));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public CentreResponse update(@PathVariable Long id, @Valid @RequestBody CentreRequest req) {
        return catalog.updateCentre(id, req);
    }

    /** Create or update the price at which this centre offers a test. */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{centreId}/tests/{testId}")
    public OfferingResponse upsertOffering(@PathVariable Long centreId, @PathVariable Long testId,
                                           @Valid @RequestBody OfferingRequest req) {
        return catalog.upsertOffering(centreId, testId, req.price());
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{centreId}/tests/{testId}")
    public ResponseEntity<Void> removeOffering(@PathVariable Long centreId, @PathVariable Long testId) {
        catalog.removeOffering(centreId, testId);
        return ResponseEntity.noContent().build();
    }
}
