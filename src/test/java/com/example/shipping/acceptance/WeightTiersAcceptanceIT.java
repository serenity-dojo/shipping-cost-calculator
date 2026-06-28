package com.example.shipping.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Verifies the HTTP contract of POST /api/shipping/calculate — request binding, response JSON
 * shape and status codes. The weight-tier, surcharge and validation rules themselves are driven
 * at the service tier in {@code ShippingCostServiceTest}; this class proves only the wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Weight-based tiered shipping (HTTP contract)")
class WeightTiersAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult calculate(String weightKg) {
        return mvc.post().uri("/api/shipping/calculate")
                .header("X-API-Key", "test-user-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "weightKg": %s, "zone": "DOMESTIC", "orderTotal": 10.00 }
                        """.formatted(weightKg))
                .exchange();
    }

    @Test
    @DisplayName("The one where a valid request returns 200 with the calculated base rate in the breakdown")
    void validRequestReturnsCalculatedCostAsJson() {
        assertThat(calculate("25.0"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.breakdown.baseRate")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("11.49");
    }

    @Test
    @DisplayName("The one where an invalid weight returns 400")
    void invalidWeightReturns400() {
        assertThat(calculate("0")).hasStatus(400);
    }
}
