package com.example.shipping.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.keys[0].key=test-user-key",
        "api.keys[0].role=USER",
        "api.keys[1].key=test-admin-key",
        "api.keys[1].role=ADMIN"
})
@DisplayName("API key authentication")
class ApiKeyAuthenticationAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private static final String VALID_REQUEST = """
            { "weightKg": 3.0, "zone": "DOMESTIC", "orderTotal": 120.00 }
            """;

    @Nested
    @DisplayName("Must reject any request to a protected endpoint that lacks a valid API key, returning 401")
    class RejectMissingOrInvalidKey {

        @Test
        @DisplayName("The one where a client POSTs to /api/shipping/calculate with no X-API-Key header — 401 with JSON error body")
        void noApiKeyReturns401() {
            var result = mvc.post().uri("/api/shipping/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST)
                    .exchange();

            assertThat(result).hasStatus(401);
            assertThat(result).bodyJson()
                    .extractingPath("$.status")
                    .isEqualTo(401);
        }

        @Test
        @DisplayName("The one where the X-API-Key header is present but matches no configured key — still 401")
        void unrecognisedApiKeyReturns401() {
            var result = mvc.post().uri("/api/shipping/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", "not-a-real-key")
                    .content(VALID_REQUEST)
                    .exchange();

            assertThat(result).hasStatus(401);
            assertThat(result).bodyJson()
                    .extractingPath("$.status")
                    .isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("Must grant access according to the role attached to the presented key")
    class RoleBasedAccess {

        @Test
        @DisplayName("The one where a USER key calls GET /api/admin/rates — 403 with JSON error body")
        void userKeyOnAdminEndpointReturns403() {
            var result = mvc.get().uri("/api/admin/rates")
                    .header("X-API-Key", "test-user-key")
                    .exchange();

            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson()
                    .extractingPath("$.status")
                    .isEqualTo(403);
        }

        @Test
        @DisplayName("The one where an ADMIN key calls GET /api/admin/rates — 200 allowed")
        void adminKeyOnAdminEndpointReturns200() {
            var result = mvc.get().uri("/api/admin/rates")
                    .header("X-API-Key", "test-admin-key")
                    .exchange();

            assertThat(result).hasStatusOk();
        }

        @Test
        @DisplayName("The one where an ADMIN key calls POST /api/shipping/calculate — 200 allowed, because ADMIN inherits USER")
        void adminKeyOnUserEndpointReturns200() {
            var result = mvc.post().uri("/api/shipping/calculate")
                    .header("X-API-Key", "test-admin-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST)
                    .exchange();

            assertThat(result).hasStatusOk();
        }
    }

    @Nested
    @DisplayName("Must keep the API documentation reachable without an API key")
    class PublicApiDocumentation {

        @Test
        @DisplayName("The one where /swagger-ui.html is opened with no header and is not blocked by security")
        void swaggerUiIsReachableWithoutApiKey() {
            var result = mvc.get().uri("/swagger-ui.html").exchange();

            assertThat(result).hasStatus3xxRedirection();
        }
    }
}
