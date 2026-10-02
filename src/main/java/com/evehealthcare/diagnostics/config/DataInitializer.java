package com.evehealthcare.diagnostics.config;

import com.evehealthcare.diagnostics.domain.*;
import com.evehealthcare.diagnostics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final DiagnosticTestRepository tests;
    private final DiagnosticCentreRepository centres;
    private final CentreTestRepository offerings;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminEmail = props.admin().email().trim().toLowerCase();
        if (!users.existsByEmail(adminEmail)) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setFullName("Administrator");
            admin.setPasswordHash(passwordEncoder.encode(props.admin().password()));
            admin.setRole(Role.ADMIN);
            users.save(admin);
            log.info("Created admin account {}", adminEmail);
        }
        if (props.seed().sampleData() && tests.count() == 0 && centres.count() == 0) {
            seedSampleData();
        }
    }

    private void seedSampleData() {
        DiagnosticTest cbc = test("Complete Blood Count (CBC)", "Haemoglobin, WBC, RBC and platelet counts");
        DiagnosticTest lipid = test("Lipid Profile", "Cholesterol and triglycerides");
        DiagnosticTest hba1c = test("HbA1c", "Average blood sugar over ~3 months");
        DiagnosticTest thyroid = test("Thyroid Profile (T3, T4, TSH)", "Thyroid function panel");
        DiagnosticTest vitD = test("Vitamin D (25-OH)", "Vitamin D level");

        DiagnosticCentre banjara = centre("EVE Diagnostics - Banjara Hills", "Hyderabad");
        DiagnosticCentre indiranagar = centre("EVE Diagnostics - Indiranagar", "Bengaluru");
        DiagnosticCentre andheri = centre("EVE Diagnostics - Andheri", "Mumbai");

        offer(banjara, cbc, "350.00");   offer(banjara, lipid, "600.00");
        offer(banjara, hba1c, "450.00"); offer(banjara, thyroid, "700.00");
        offer(indiranagar, cbc, "399.00"); offer(indiranagar, lipid, "650.00"); offer(indiranagar, vitD, "1200.00");
        offer(andheri, cbc, "420.00");   offer(andheri, hba1c, "500.00");
        offer(andheri, thyroid, "750.00"); offer(andheri, vitD, "1100.00");
        log.info("Seeded sample diagnostic tests and centres");
    }

    private DiagnosticTest test(String name, String description) {
        DiagnosticTest t = new DiagnosticTest();
        t.setName(name);
        t.setDescription(description);
        return tests.save(t);
    }

    private DiagnosticCentre centre(String name, String location) {
        DiagnosticCentre c = new DiagnosticCentre();
        c.setName(name);
        c.setLocation(location);
        return centres.save(c);
    }

    private void offer(DiagnosticCentre c, DiagnosticTest t, String price) {
        CentreTest ct = new CentreTest();
        ct.setCentre(c);
        ct.setTest(t);
        ct.setPrice(new BigDecimal(price));
        offerings.save(ct);
    }
}
