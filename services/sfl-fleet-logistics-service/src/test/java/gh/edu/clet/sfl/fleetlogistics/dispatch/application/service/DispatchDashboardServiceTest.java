package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.support.DispatchTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: S171 operational dashboards - counts, the stale-data flag, and CSV exports. */
class DispatchDashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private FleetTestDoubles.FixedRuntimeConfiguration runtimeConfig;
    private DispatchDashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        runtimeConfig = new FleetTestDoubles.FixedRuntimeConfiguration().withDashboardFreshness(Duration.ofMinutes(15));
        service = new DispatchDashboardService(repository, new DispatchAccessPolicy(), runtimeConfig, clock);
    }

    @Test
    @DisplayName("a dashboard read within the freshness window returns counts and stale=false")
    void dashboard_within_freshness_window_is_not_stale() {
        repository.withDashboardSourceUpdatedAt(NOW.minusSeconds(60));

        var result = service.dashboard(SITE, DispatchTestDoubles.dispatchController(SITE));

        assertThat(result.get("stale")).isEqualTo(false);
    }

    @Test
    @DisplayName("a dashboard read past the freshness window returns stale=true")
    void dashboard_past_freshness_window_is_stale() {
        repository.withDashboardSourceUpdatedAt(NOW.minus(Duration.ofHours(2)));

        var result = service.dashboard(SITE, DispatchTestDoubles.dispatchController(SITE));

        assertThat(result.get("stale")).isEqualTo(true);
    }

    @Test
    @DisplayName("reading the dashboard is denied to an actor without DISPATCH_REPORT_READ")
    void dashboard_is_denied_without_report_read_permission() {
        var mailroom = DispatchTestDoubles.mailroomOfficer(SITE);

        assertThatThrownBy(() -> service.dashboard(SITE, mailroom)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("exporting the items report is denied to an actor who can only read, not export, reports")
    void itemsReportCsv_is_denied_without_report_export_permission() {
        var centreManager = DispatchTestDoubles.centreManager(SITE);

        assertThatThrownBy(() -> service.itemsReportCsv(SITE, centreManager))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("the items report CSV carries its header even with no items registered")
    void itemsReportCsv_happy_path_returns_the_header_row() {
        String csv = service.itemsReportCsv(SITE, DispatchTestDoubles.dispatchManager(SITE));

        assertThat(csv).startsWith("itemNumber,direction,itemType");
    }
}
