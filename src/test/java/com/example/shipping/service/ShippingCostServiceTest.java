package com.example.shipping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.shipping.model.InvalidOrderTotalException;
import com.example.shipping.model.InvalidWeightException;
import com.example.shipping.model.InvalidZoneException;
import com.example.shipping.model.ShippingCost;
import com.example.shipping.model.ShippingRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Weight-based tiered shipping")
class ShippingCostServiceTest {

    private final ShippingCostService service = new ShippingCostService();

    private BigDecimal baseRateFor(String weightKg) {
        ShippingCost cost = service.calculate(
                new ShippingRequest(new BigDecimal(weightKg), "DOMESTIC", new BigDecimal("10.00")));
        return cost.breakdown().baseRate();
    }

    @Nested
    @DisplayName("Must set the base rate from the parcel's weight tier, with each tier's lower bound inclusive")
    class WeightTierBaseRate {

        @ParameterizedTest(name = "The one where {2}")
        @CsvSource(delimiter = '|', textBlock = """
                # weightKg | baseRate | description
                  0.5       | 2.99     | a 0.5kg parcel (under 1kg) has a base rate of £2.99
                  3.0       | 4.99     | a 3kg parcel (1–5kg tier) has a base rate of £4.99
                  12.0      | 8.99     | a 12kg parcel (5–20kg tier) has a base rate of £8.99
                  1.0       | 4.99     | a 1kg parcel sits on the lower bound of the 1–5kg tier, giving £4.99
                  5.0       | 8.99     | a 5kg parcel sits on the lower bound of the 5–20kg tier, giving £8.99
                  20.0      | 8.99     | a 20kg parcel sits on the lower bound of the over-20kg tier, giving £8.99 with no surcharge
                """)
        void baseRateForWeightTier(String weightKg, String expectedBaseRate, String description) {
            assertThat(baseRateFor(weightKg)).isEqualByComparingTo(expectedBaseRate);
        }
    }

    @Nested
    @DisplayName("Must add a surcharge of £0.50 per kg over 20kg, pro-rata to the exact weight, on top of the £8.99 base")
    class OverTwentyKgSurcharge {

        @ParameterizedTest(name = "The one where {2}")
        @CsvSource(delimiter = '|', textBlock = """
                # weightKg | baseRate | description
                  22.5      | 10.24    | a 22.5kg parcel costs £8.99 + (£0.50 × 2.5kg over) = £10.24
                  25.0      | 11.49    | a 25kg parcel costs £8.99 + (£0.50 × 5kg over) = £11.49
                  20.0      | 8.99     | a 20kg parcel has no surcharge — the excess is 0, so the base rate stays at £8.99
                """)
        void surchargeAddedProRataForWeightOver20kg(String weightKg, String expectedBaseRate, String description) {
            assertThat(baseRateFor(weightKg)).isEqualByComparingTo(expectedBaseRate);
        }
    }

    @Nested
    @DisplayName("Must scale the weight-based cost by the destination zone's multiplier, applied after the weight-tier step")
    class ZoneMultiplierScalesWeightCost {

        @ParameterizedTest(name = "The one where {3}")
        @CsvSource(delimiter = '|', textBlock = """
                # weightKg | zone          | expectedTotal | description
                  3.0       | DOMESTIC      | 4.99          | a 3kg DOMESTIC parcel costs £4.99 (×1.0)
                  3.0       | EUROPEAN      | 7.49          | a 3kg EUROPEAN parcel costs £7.49 (×1.5)
                  3.0       | INTERNATIONAL | 12.48         | a 3kg INTERNATIONAL parcel costs £12.48 (×2.5)
                  25.0      | INTERNATIONAL | 28.73         | a 25kg INTERNATIONAL parcel costs £28.73 (base £11.49 ×2.5)
                """)
        void zoneMultiplierScalesWeightCost(String weightKg, String zone, String expectedTotal, String description) {
            ShippingCost cost = service.calculate(
                    new ShippingRequest(new BigDecimal(weightKg), zone, new BigDecimal("10.00")));
            assertThat(cost.totalCost()).isEqualByComparingTo(expectedTotal);
        }
    }

    @Nested
    @DisplayName("Must reject a request whose zone is missing, empty, or not one of the three recognised zones")
    class InvalidZoneRejection {

        @ParameterizedTest(name = "The one where zone \"{0}\" is rejected as unrecognised")
        @ValueSource(strings = {"ANTARCTIC", "", "  "})
        void unrecognisedOrEmptyZoneIsRejected(String zone) {
            assertThatExceptionOfType(InvalidZoneException.class)
                    .isThrownBy(() -> service.calculate(
                            new ShippingRequest(new BigDecimal("3.0"), zone, new BigDecimal("10.00"))));
        }
    }

    @Nested
    @DisplayName("Must waive shipping (total cost = £0.00) only when the order is Domestic or European, its order total is at least £50.00, and the parcel weighs 20kg or less")
    class FreeShippingQualification {

        @ParameterizedTest(name = "The one where {5}")
        @CsvSource(delimiter = '|', textBlock = """
                # weightKg | zone          | orderTotal | expectedTotal | freeShipping | description
                  3.0       | DOMESTIC      | 120.00     | 0.00          | true         | a Domestic 3kg order totalling £120.00 ships free (£0.00)
                  3.0       | DOMESTIC      | 50.00      | 0.00          | true         | a Domestic 3kg order at exactly the £50.00 threshold ships free
                  3.0       | DOMESTIC      | 49.99      | 4.99          | false        | a Domestic 3kg order one penny under the threshold pays the full £4.99
                  20.0      | DOMESTIC      | 120.00     | 0.00          | true         | a Domestic 20kg order at the weight cap still ships free
                  25.0      | DOMESTIC      | 120.00     | 11.49         | false        | a Domestic 25kg order over the 20kg cap pays the surcharged rate despite a qualifying total
                  3.0       | EUROPEAN      | 120.00     | 0.00          | true         | a European 3kg order totalling £120.00 now ships free (£0.00)
                  3.0       | EUROPEAN      | 50.00      | 0.00          | true         | a European 3kg order at exactly the £50.00 threshold ships free
                  3.0       | EUROPEAN      | 49.99      | 7.49          | false        | a European 3kg order one penny under the threshold pays the full £7.49
                  3.0       | european      | 120.00     | 0.00          | true         | a lowercase european order qualifies — zone matching is case-insensitive
                  3.0       | INTERNATIONAL | 500.00     | 12.48         | false        | an International order never qualifies, regardless of order total
                """)
        void freeShippingAppliesOnlyToQualifyingDomesticAndEuropeanOrders(
                String weightKg, String zone, String orderTotal,
                String expectedTotal, boolean freeShipping, String description) {
            ShippingCost cost = service.calculate(
                    new ShippingRequest(new BigDecimal(weightKg), zone, new BigDecimal(orderTotal)));
            assertThat(cost.totalCost()).isEqualByComparingTo(expectedTotal);
            assertThat(cost.breakdown().freeShippingApplied()).isEqualTo(freeShipping);
        }
    }

    @Nested
    @DisplayName("Must require a valid order total on every request, rejecting a missing or negative value")
    class OrderTotalValidation {

        @ParameterizedTest(name = "The one where an order total of £{0} is rejected as invalid")
        @ValueSource(strings = {"-10.00", "-0.01"})
        void negativeOrderTotalIsRejected(String orderTotal) {
            assertThatExceptionOfType(InvalidOrderTotalException.class)
                    .isThrownBy(() -> service.calculate(
                            new ShippingRequest(new BigDecimal("3.0"), "DOMESTIC", new BigDecimal(orderTotal))));
        }

        @Test
        @DisplayName("The one where the order total is missing (null) — rejected as invalid")
        void missingOrderTotalIsRejected() {
            assertThatExceptionOfType(InvalidOrderTotalException.class)
                    .isThrownBy(() -> service.calculate(
                            new ShippingRequest(new BigDecimal("3.0"), "DOMESTIC", null)));
        }

        @Test
        @DisplayName("The one where the order total is £0.00 — accepted, priced normally below the threshold")
        void zeroOrderTotalIsAcceptedAndPaysNormalRate() {
            ShippingCost cost = service.calculate(
                    new ShippingRequest(new BigDecimal("3.0"), "DOMESTIC", new BigDecimal("0.00")));
            assertThat(cost.totalCost()).isEqualByComparingTo("4.99");
            assertThat(cost.breakdown().freeShippingApplied()).isFalse();
        }
    }

    @Nested
    @DisplayName("Must reject a parcel whose weight is zero, negative, or above 50kg")
    class InvalidWeightRejection {

        @ParameterizedTest(name = "The one where a weight of {0}kg is rejected as invalid")
        @ValueSource(strings = {"0", "-2.0", "50.01"})
        void invalidWeightIsRejected(String weightKg) {
            assertThatExceptionOfType(InvalidWeightException.class)
                    .isThrownBy(() -> baseRateFor(weightKg));
        }

        @ParameterizedTest(name = "The one where {2}")
        @CsvSource(delimiter = '|', textBlock = """
                # weightKg | baseRate | description
                  0.01      | 2.99     | a tiny positive weight (0.01kg) is accepted and charged £2.99
                  50.0      | 23.99    | a 50kg parcel is accepted and costs £8.99 + (£0.50 × 30) = £23.99
                """)
        void validBoundaryWeightIsAccepted(String weightKg, String expectedBaseRate, String description) {
            assertThat(baseRateFor(weightKg)).isEqualByComparingTo(expectedBaseRate);
        }
    }
}