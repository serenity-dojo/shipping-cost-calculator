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
@DisplayName("Distance-zone shipping adjustment")
class DistanceZonesAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult calculate(String weightKg, String zone) {
        return mvc.post().uri("/api/shipping/calculate")
                .header("X-API-Key", "test-user-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "weightKg": %s, "zone": "%s", "orderTotal": 10.00 }
                        """.formatted(weightKg, zone))
                .exchange();
    }

    @Nested
    @DisplayName("Must scale the weight-based cost by the destination zone's multiplier, applied after the weight-tier step")
    class ZoneMultiplierScalesWeightCost {

        @Test
        @DisplayName("The one where a 3kg European parcel has a total cost of £7.49 in the response")
        void zonedTotalIsReturnedInTheResponse() {
            assertThat(calculate("3.0", "EUROPEAN"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("7.49");
        }
    }

    @Nested
    @DisplayName("Must reject a request whose zone is missing, empty, or not one of the three recognised zones")
    class ZoneRejection {

        @Test
        @DisplayName("The one where the zone is \"ANTARCTIC\" and the request is rejected as an invalid request")
        void unrecognisedZoneIsRejected() {
            assertThat(calculate("3.0", "ANTARCTIC")).hasStatus(400);
        }

        @Test
        @DisplayName("The one where the zone is missing — rejected the same as an unknown value, not defaulted")
        void missingZoneIsRejected() {
            MvcTestResult result = mvc.post().uri("/api/shipping/calculate")
                    .header("X-API-Key", "test-user-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "weightKg": 3.0, "orderTotal": 10.00 }
                            """)
                    .exchange();
            assertThat(result).hasStatus(400);
        }

        @Test
        @DisplayName("The one where the zone is empty — rejected the same as an unknown value, not defaulted")
        void emptyZoneIsRejected() {
            MvcTestResult result = mvc.post().uri("/api/shipping/calculate")
                    .header("X-API-Key", "test-user-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "weightKg": 3.0, "zone": "", "orderTotal": 10.00 }
                            """)
                    .exchange();
            assertThat(result).hasStatus(400);
        }

        @ParameterizedTest(name = "The one where the zone is \"{0}\" — accepted and priced at £{1}, because matching ignores case")
        @CsvSource({
                "domestic,      4.99",
                "INTERNATIONAL, 12.48",
        })
        void caseInsensitiveZoneIsAcceptedAndPriced(String zone, String expectedTotal) {
            assertThat(calculate("3.0", zone))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalCost")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(expectedTotal);
        }
    }
}
