package com.evehealthcare.diagnostics.service;

import com.evehealthcare.diagnostics.domain.CentreTest;
import com.evehealthcare.diagnostics.domain.DiagnosticCentre;
import com.evehealthcare.diagnostics.domain.DiagnosticTest;
import com.evehealthcare.diagnostics.dto.*;
import com.evehealthcare.diagnostics.repository.CentreTestRepository;
import com.evehealthcare.diagnostics.repository.DiagnosticCentreRepository;
import com.evehealthcare.diagnostics.repository.DiagnosticTestRepository;
import com.evehealthcare.diagnostics.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final DiagnosticTestRepository tests;
    private final DiagnosticCentreRepository centres;
    private final CentreTestRepository offerings;


    @Transactional
    public TestResponse createTest(TestRequest req) {
        String name = req.name().trim();
        if (tests.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A test named '" + name + "' already exists");
        }
        DiagnosticTest t = new DiagnosticTest();
        t.setName(name);
        t.setDescription(req.description());
        return TestResponse.from(tests.saveAndFlush(t));
    }

    @Transactional
    public TestResponse updateTest(Long id, TestRequest req) {
        DiagnosticTest t = findTest(id);
        String name = req.name().trim();
        if (!t.getName().equalsIgnoreCase(name) && tests.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A test named '" + name + "' already exists");
        }
        t.setName(name);
        t.setDescription(req.description());
        return TestResponse.from(tests.saveAndFlush(t));
    }

    @Transactional(readOnly = true)
    public TestResponse getTest(Long id) {
        return TestResponse.from(findTest(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TestResponse> listTests(String q, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("name").ascending().and(Sort.by("id")));
        return PageResponse.of(tests.findByNameContainingIgnoreCase(q == null ? "" : q.trim(), pageable),
                TestResponse::from);
    }


    @Transactional
    public CentreResponse createCentre(CentreRequest req) {
        String name = req.name().trim();
        String location = req.location().trim();
        if (centres.existsByNameIgnoreCaseAndLocationIgnoreCase(name, location)) {
            throw ApiException.conflict("A centre with this name already exists at this location");
        }
        DiagnosticCentre c = new DiagnosticCentre();
        c.setName(name);
        c.setLocation(location);
        return toResponse(centres.saveAndFlush(c), List.of());
    }

    @Transactional
    public CentreResponse updateCentre(Long id, CentreRequest req) {
        DiagnosticCentre c = findCentre(id);
        String name = req.name().trim();
        String location = req.location().trim();
        boolean changed = !c.getName().equalsIgnoreCase(name) || !c.getLocation().equalsIgnoreCase(location);
        if (changed && centres.existsByNameIgnoreCaseAndLocationIgnoreCase(name, location)) {
            throw ApiException.conflict("A centre with this name already exists at this location");
        }
        c.setName(name);
        c.setLocation(location);
        centres.saveAndFlush(c);
        return toResponse(c, offerings.findByCentreId(id));
    }

    @Transactional(readOnly = true)
    public CentreResponse getCentre(Long id) {
        return toResponse(findCentre(id), offerings.findByCentreId(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CentreResponse> listCentres(String location, Long testId, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by("name").ascending().and(Sort.by("id")));
        String loc = location == null ? "" : location.trim();
        Page<DiagnosticCentre> result = (testId == null)
                ? centres.findByLocationContainingIgnoreCase(loc, pageable)
                : centres.searchByLocationAndTest("%" + loc.toLowerCase(Locale.ROOT) + "%", testId, pageable);

        List<Long> ids = result.getContent().stream().map(DiagnosticCentre::getId).toList();
        Map<Long, List<CentreTest>> byCentre = ids.isEmpty() ? Map.of()
                : offerings.findByCentreIdIn(ids).stream()
                        .collect(Collectors.groupingBy(ct -> ct.getCentre().getId()));
        List<CentreResponse> content = result.getContent().stream()
                .map(c -> toResponse(c, byCentre.getOrDefault(c.getId(), List.of())))
                .toList();
        return PageResponse.from(result, content);
    }


    @Transactional
    public OfferingResponse upsertOffering(Long centreId, Long testId, BigDecimal price) {
        DiagnosticCentre centre = findCentre(centreId);
        DiagnosticTest test = findTest(testId);
        CentreTest ct = offerings.findByCentreIdAndTestId(centreId, testId).orElseGet(() -> {
            CentreTest n = new CentreTest();
            n.setCentre(centre);
            n.setTest(test);
            return n;
        });
        ct.setPrice(price);
        return OfferingResponse.from(offerings.saveAndFlush(ct));
    }

    @Transactional
    public void removeOffering(Long centreId, Long testId) {
        CentreTest ct = offerings.findByCentreIdAndTestId(centreId, testId)
                .orElseThrow(() -> ApiException.notFound("This centre does not offer that test"));
        offerings.delete(ct);   // existing bookings keep their own price snapshot, so they are unaffected
    }


    private DiagnosticTest findTest(Long id) {
        return tests.findById(id).orElseThrow(() -> ApiException.notFound("Test " + id + " not found"));
    }

    private DiagnosticCentre findCentre(Long id) {
        return centres.findById(id).orElseThrow(() -> ApiException.notFound("Centre " + id + " not found"));
    }

    private CentreResponse toResponse(DiagnosticCentre c, List<CentreTest> cts) {
        List<OfferingResponse> list = cts.stream()
                .map(OfferingResponse::from)
                .sorted(Comparator.comparing(OfferingResponse::testName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new CentreResponse(c.getId(), c.getName(), c.getLocation(), list);
    }
}
