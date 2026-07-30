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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Postgres-backed end-to-end verification for the fleet S166 module. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sfl.security.enabled=false",
        "sfl.fleet.scheduling.sla.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false",
        "sfl.fleet.scheduling.compliance.enabled=false",
        "sfl.fleet.messaging.transport=local"
})
class FleetPostgresEndToEndTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sfl__fleet_vehicle_service_e2e")
            .withUsername("sfl")
            .withPassword("sfl");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway, the served dashboard page and the live dashboard endpoint work against Postgres")
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
        String page = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/fleet/index.html"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString())
                .body();
        assertThat(page).contains("Fleet dashboard");

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
