package com.evehealthcare.diagnostics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiTest extends BaseApiTest {

    @Test
    void adminManagesCatalogAndAnyoneCanRead() throws Exception {
        Catalog c = createCatalog("1250.50");

        get("/centres/" + c.centreId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Hyderabad"))
                .andExpect(jsonPath("$.tests", hasSize(1)))
                .andExpect(jsonPath("$.tests[0].testId").value(c.testId()))
                .andExpect(jsonPath("$.tests[0].price").value(1250.50));

        get("/centres?testId=" + c.testId() + "&location=hyder", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c.centreId()));

        get("/tests/" + c.testId(), null).andExpect(status().isOk());

        put("/centres/" + c.centreId() + "/tests/" + c.testId(), adminToken(), Map.of("price", new BigDecimal("999.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(999.00));
    }

    @Test
    void nonAdminsCannotModifyCatalog() throws Exception {
        String user = newUserToken();
        post("/tests", user, Map.of("name", "X-" + UUID.randomUUID())).andExpect(status().isForbidden());
        post("/centres", user, Map.of("name", "C", "location", "L")).andExpect(status().isForbidden());
        put("/centres/1/tests/1", user, Map.of("price", 10)).andExpect(status().isForbidden());

        post("/tests", null, Map.of("name", "X-" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void validationAndConflicts() throws Exception {
        String admin = adminToken();
        String name = "Dup-" + UUID.randomUUID();
        post("/tests", admin, Map.of("name", name)).andExpect(status().isCreated());
        post("/tests", admin, Map.of("name", name.toUpperCase())).andExpect(status().isConflict());
        post("/tests", admin, Map.of("name", "  ")).andExpect(status().isBadRequest());

        Catalog c = createCatalog("100.00");
        put("/centres/" + c.centreId() + "/tests/" + c.testId(), admin, Map.of("price", 0)).andExpect(status().isBadRequest());
        put("/centres/" + c.centreId() + "/tests/" + c.testId(), admin, Map.of("price", -5)).andExpect(status().isBadRequest());
        put("/centres/" + c.centreId() + "/tests/" + c.testId(), admin, Map.of("price", new BigDecimal("1.234"))).andExpect(status().isBadRequest());
        put("/centres/999999/tests/" + c.testId(), admin, Map.of("price", 10)).andExpect(status().isNotFound());
        put("/centres/" + c.centreId() + "/tests/999999", admin, Map.of("price", 10)).andExpect(status().isNotFound());
    }

    @Test
    void unknownIdsAndBadIdsAreHandled() throws Exception {
        get("/centres/999999", null).andExpect(status().isNotFound());
        get("/tests/999999", null).andExpect(status().isNotFound());
        get("/centres/not-a-number", null).andExpect(status().isBadRequest());
    }

    @Test
    void paginationIsClamped() throws Exception {
        createCatalog("10.00");
        get("/centres?size=100000&page=-4", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }
}
