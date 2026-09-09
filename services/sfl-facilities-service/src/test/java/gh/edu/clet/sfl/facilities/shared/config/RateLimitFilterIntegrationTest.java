package gh.edu.clet.sfl.facilities.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code RateLimitAutoConfiguration} wired end to end, with the budget turned down low enough for a
 * test to exhaust it deliberately.
 *
 * <p>A real embedded server, not {@code @AutoConfigureMockMvc}: {@link RateLimitFilter} is registered
 * as a {@code FilterRegistrationBean}, which only takes effect in a real servlet container's filter
 * chain - {@code MockMvc}'s {@code MOCK} web environment never builds one, so a
 * {@code FilterRegistrationBean}-registered filter silently never runs there. That gap is exactly what
 * made this the wrong way to find out: the filter is proven here with a live socket, not assumed.
 *
 * <p>Off by default estate-wide (see {@code RateLimitAutoConfiguration}'s Javadoc), so this is the one
 * place it is proven to actually throttle when a deployment turns it on - everywhere else in the suite
 * runs with it off, which is necessary so the estate's existing request volume across hundreds of tests
 * never trips it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "sfl.security.enabled=false",
        "sfl.rate-limit.enabled=true",
        "sfl.rate-limit.requests-per-window=3",
        "sfl.rate-limit.window-seconds=60",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class RateLimitFilterIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("a caller past the configured budget gets 429, not the endpoint's own response")
    void throttles_past_the_configured_budget() throws Exception {
        // Not one of RateLimitProperties' default exclusions (system/info, actuator, docs, webhooks) -
        // an ordinary endpoint, reachable with no actor headers because security is off here.
        String path = "/api/v1/facilities/sites";

        // Not asserting any particular status for these three: the actor headers are unset, so the
        // endpoint's own authorization may well refuse the request downstream. What "within budget"
        // means here is only that the filter let the request through to find that out.
        for (int i = 0; i < 3; i++) {
            int attempt = i + 1;
            int status = get(path);
            assertThat(status).as("request %d of 3 (within budget)", attempt).isNotEqualTo(429);
        }

        assertThat(get(path)).as("request past the budget").isEqualTo(429);
    }

    @Test
    @DisplayName("the health probe is excluded from the budget by default")
    void excludes_the_health_probe() throws Exception {
        for (int i = 0; i < 5; i++) {
            int attempt = i + 1;
            assertThat(get("/actuator/health")).as("health probe call %d", attempt).isNotEqualTo(429);
        }
    }

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}
