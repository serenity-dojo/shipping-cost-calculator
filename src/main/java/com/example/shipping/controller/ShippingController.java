package com.example.shipping.controller;

import com.example.shipping.model.InvalidOrderTotalException;
import com.example.shipping.model.InvalidWeightException;
import com.example.shipping.model.InvalidZoneException;
import com.example.shipping.model.ShippingCost;
import com.example.shipping.model.ShippingRequest;
import com.example.shipping.service.ShippingCostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingCostService service;

    public ShippingController(ShippingCostService service) {
        this.service = service;
    }

    @PostMapping("/calculate")
    public ShippingCost calculate(@RequestBody ShippingRequest request) {
        return service.calculate(request);
    }

    @ExceptionHandler({InvalidWeightException.class, InvalidZoneException.class, InvalidOrderTotalException.class})
    public ResponseEntity<Void> handleInvalidRequest() {
        return ResponseEntity.badRequest().build();
    }
}
