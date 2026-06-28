package com.example.shipping.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Free shipping on qualifying domestic and European orders")
class FreeShippingAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult calculate(String weightKg, String zone, String orderTotal) {
        return mvc.post().uri("/api/shipping/calculate")
                .header("X-API-Key", "test-user-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "weightKg": %s, "zone": "%s", "orderTotal": %s }
                        """.formatted(weightKg, zone, orderTotal))
                .exchange();
    }

    @Nested
    @DisplayName("Must waive shipping (total cost = £0.00) only when the order is Domestic or European, its order total is at least £50.00, and the parcel weighs 20kg or less")
    class FreeShippingQualification {

        @ParameterizedTest(name = "The one where a {0}kg {1} order of £{2} costs £{3}")
        @CsvSource({
                // weight, zone,          orderTotal, totalCost
                "3.0,  DOMESTIC,      120.00, 0.00",   // qualifies
                "3.0,  DOMESTIC,      50.00,  0.00",   // boundary: exactly £50 qualifies
                "3.0,  DOMESTIC,      49.99,  4.99",   // one penny under, pays
                "20.0, DOMESTIC,      120.00, 0.00",   // boundary: 20kg still qualifies
                "25.0, DOMESTIC,      120.00, 11.49",  // over 20kg, pays despite qualifying total
                "3.0,  EUROPEAN,      120.00, 0.00",   // European now qualifies
                "3.0,  EUROPEAN,      50.00,  0.00",   // boundary: exactly £50 qualifies
                "3.0,  EUROPEAN,      49.99,  7.49",   // one penny under, pays
                "3.0,  INTERNATIONAL, 500.00, 12.48",  // International never qualifies
        })
        void freeShippingAppliesOnlyToQualifyingDomesticAndEuropeanOrders(
                String weightKg, String zone, String orderTotal, String expectedTotal) {
            assertThat(calculate(weightKg, zone, orderTotal))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("The one where a qualifying domestic order reports freeShippingApplied=true in the breakdown")
        void qualifyingOrderFlagsFreeShippingInTheBreakdown() {
            assertThat(calculate("3.0", "DOMESTIC", "120.00"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.breakdown.freeShippingApplied")
                    .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Must require a valid order total on every request, rejecting a missing or negative value")
    class OrderTotalValidation {

        @ParameterizedTest(name = "The one where an order total of £{0} is accepted")
        @CsvSource({
                "120.00",  // priced normally
                "0.00",    // valid, below threshold, no free shipping
        })
        void validOrderTotalIsAccepted(String orderTotal) {
            assertThat(calculate("3.0", "DOMESTIC", orderTotal)).hasStatusOk();
        }

        @Test
        @DisplayName("The one where the order total is missing — rejected as an invalid request")
        void missingOrderTotalIsRejected() {
            MvcTestResult result = mvc.post().uri("/api/shipping/calculate")
                    .header("X-API-Key", "test-user-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "weightKg": 3.0, "zone": "DOMESTIC" }
                            """)
                    .exchange();
            assertThat(result).hasStatus(400);
        }

        @Test
        @DisplayName("The one where the order total is negative (−£10.00) — rejected as an invalid request")
        void negativeOrderTotalIsRejected() {
            assertThat(calculate("3.0", "DOMESTIC", "-10.00")).hasStatus(400);
        }
    }
}
