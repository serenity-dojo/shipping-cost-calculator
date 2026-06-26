package com.example.shipping.service;

import com.example.shipping.model.InvalidWeightException;
import com.example.shipping.model.InvalidZoneException;
import com.example.shipping.model.ShippingCost;
import com.example.shipping.model.ShippingRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class ShippingCostService {

    public ShippingCost calculate(ShippingRequest request) {
        BigDecimal weight = request.weightKg();
        if (weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(new BigDecimal("50")) > 0) {
            throw new InvalidWeightException("Weight must be greater than 0 and at most 50kg");
        }
        BigDecimal baseRate = baseRate(weight);
        BigDecimal multiplier = zoneMultiplier(request.zone());
        BigDecimal zonedRate = baseRate.multiply(multiplier);
        BigDecimal totalCost = zonedRate.setScale(2, RoundingMode.HALF_UP);
        return new ShippingCost(totalCost,
                new ShippingCost.Breakdown(baseRate, multiplier, zonedRate, false));
    }

    private BigDecimal zoneMultiplier(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new InvalidZoneException("Zone must not be blank");
        }
        return switch (zone.toUpperCase()) {
            case "DOMESTIC" -> BigDecimal.ONE;
            case "EUROPEAN" -> new BigDecimal("1.5");
            case "INTERNATIONAL" -> new BigDecimal("2.5");
            default -> throw new InvalidZoneException("Unknown zone: " + zone);
        };
    }

    private BigDecimal baseRate(BigDecimal weightKg) {
        if (weightKg.compareTo(new BigDecimal("1")) < 0) {
            return new BigDecimal("2.99");
        }
        if (weightKg.compareTo(new BigDecimal("5")) < 0) {
            return new BigDecimal("4.99");
        }
        if (weightKg.compareTo(new BigDecimal("20")) > 0) {
            BigDecimal surcharge = weightKg.subtract(new BigDecimal("20"))
                    .multiply(new BigDecimal("0.50"))
                    .setScale(2, RoundingMode.HALF_UP);
            return new BigDecimal("8.99").add(surcharge);
        }
        return new BigDecimal("8.99");
    }
}
