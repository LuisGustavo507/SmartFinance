package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Exercises the runtime exactly as it runs behind a TLS-terminating proxy:
 * a real embedded server, real filters and forwarded headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class ProductionRuntimeIntegrationTests {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void healthIsPublicAndDoesNotExposeDetails() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist();
    }

    @Test
    void readinessProbeIsPublic() {
        client.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void forwardedHttpsReceivesProductionSecurityHeaders() {
        client.get().uri("/api/auth/csrf")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().value("Strict-Transport-Security",
                        value -> assertThat(value).contains("max-age=31536000"))
                .expectHeader().value("Strict-Transport-Security",
                        value -> assertThat(value).contains("includeSubDomains"))
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().value("Permissions-Policy",
                        value -> assertThat(value).contains("geolocation=()", "camera=()"))
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    void plainHttpDoesNotSendHsts() {
        client.get().uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().doesNotExist("Strict-Transport-Security");
    }

    @Test
    void actuatorRootStaysProtected() {
        client.get().uri("/actuator")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
