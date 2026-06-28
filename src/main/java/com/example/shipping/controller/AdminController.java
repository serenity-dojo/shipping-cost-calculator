package com.example.shipping.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints. Access is restricted to the ADMIN role by the security chain. The
 * rate-listing behaviour itself is a separate feature; this returns a placeholder for now.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/rates")
    public Map<String, List<Object>> rates() {
        return Map.of("rates", List.of());
    }
}
