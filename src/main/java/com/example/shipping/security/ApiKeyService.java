package com.example.shipping.security;

import java.util.List;
import java.util.Optional;

public class ApiKeyService {

    private final List<ApiKeyProperties.ApiKey> keys;

    public ApiKeyService(List<ApiKeyProperties.ApiKey> keys) {
        this.keys = keys;
    }

    public Optional<String> findRole(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return keys.stream()
                .filter(e -> e.key() != null && !e.key().isBlank())
                .filter(e -> e.key().equals(key))
                .map(ApiKeyProperties.ApiKey::role)
                .findFirst();
    }
}
