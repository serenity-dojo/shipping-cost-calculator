package com.example.shipping.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api")
public class ApiKeyProperties {

    private List<ApiKey> keys = new ArrayList<>();

    public List<ApiKey> getKeys() {
        return keys;
    }

    public void setKeys(List<ApiKey> keys) {
        this.keys = keys;
    }

    public record ApiKey(String key, String role) {}
}
