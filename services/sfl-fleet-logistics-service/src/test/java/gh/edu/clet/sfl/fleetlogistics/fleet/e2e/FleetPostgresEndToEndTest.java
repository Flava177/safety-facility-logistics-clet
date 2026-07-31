package gh.edu.clet.sfl.fleetlogistics.fleet.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Postgres-backed end-to-end verification for the fleet S166 module.
 *
 * <p>Gated on {@link FleetPostgresSupport#databaseAvailable()} like every other suite in this package.
 * It was the last class still gated on {@code @Testcontainers(disabledWithoutDocker = true)}, which
 * asks whether the <em>Java</em> Docker client can reach the daemon — a question that answers "no" on
 * Windows even while the daemon runs and {@code docker ps} works. So this test skipped on every run
 * here, and a skip reads as a pass in a summary line.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sfl.security.enabled=false",
        "sfl.fleet.scheduling.sla.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false",
        "sfl.fleet.scheduling.compliance.enabled=false",
        "sfl.fleet.messaging.transport=local"
})
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FleetPostgresSupport.unavailableReason()")
class FleetPostgresEndToEndTest extends FleetPostgresSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway, the retired /fleet route and the live dashboard endpoint work against Postgres")
    void fleet_module_runs_end_to_end_on_postgres() throws Exception {
        Integer migratedTables = jdbc.queryForObject("""
                select count(*)
                  from information_schema.tables
                 where table_schema = 'fleet_logistics'
                   and table_name in (
                       'fleet_evidence_references',
                       'fleet_integration_inbox_messages',
                       'fleet_vehicle_locations',
                       'fleet_dashboard_snapshots'
                   )
                """, Integer.class);
        assertThat(migratedTables).isEqualTo(4);

        HttpClient client = HttpClient.newHttpClient();

        // `/fleet` used to serve a page of its own; ADR 0006 retired it. The notice page is asserted
        // on rather than the redirect, because the redirect is registered only when the dashboard
        // bundle has been copied in and this test must pass either way. What matters both ways is
        // that the route names where the screens went instead of dead-ending.
        String page = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/fleet/index.html"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString())
                .body();
        assertThat(page).contains("/ui/fleet");
        assertThat(page).contains("has moved to the SFL Operations dashboard");

        String dashboard = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port
                                + "/api/v1/fleet/dashboards/operations?siteCode=ACCRA"))
                        .header("X-SFL-User", "manager@clet.edu.gh")
                        .header("X-SFL-Roles", "FLEET_MANAGER")
                        .header("X-SFL-Sites", "ACCRA")
                        .header("X-Correlation-ID", "fleet-e2e")
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString())
                .body();
        assertThat(dashboard).contains("\"data\"", "\"indicators\"");
    }
}
