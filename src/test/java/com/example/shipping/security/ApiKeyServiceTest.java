package com.example.shipping.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyServiceTest {

    @Test
    @DisplayName("The one where a known key returns its configured role")
    void knownKeyReturnsItsRole() {
        var service = new ApiKeyService(List.of(new ApiKeyProperties.ApiKey("test-user-key", "USER")));

        assertThat(service.findRole("test-user-key")).contains("USER");
    }

    @Test
    @DisplayName("The one where an unconfigured key matches no role")
    void unknownKeyReturnsEmpty() {
        var service = new ApiKeyService(List.of(new ApiKeyProperties.ApiKey("test-user-key", "USER")));

        assertThat(service.findRole("not-a-real-key")).isEmpty();
    }

    @Test
    @DisplayName("The one where a blank presented key never matches a blank (unconfigured) key")
    void blankKeyNeverMatches() {
        var service = new ApiKeyService(List.of(new ApiKeyProperties.ApiKey("", "USER")));

        assertThat(service.findRole("")).isEmpty();
    }
}
