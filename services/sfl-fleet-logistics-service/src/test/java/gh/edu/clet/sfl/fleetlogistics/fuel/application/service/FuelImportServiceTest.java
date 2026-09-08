package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverScopeResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelImportAlreadyProcessedException;
import gh.edu.clet.sfl.fleetlogistics.fuel.support.FuelTestDoubles;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S168fuel bulk CSV import - the provider-neutral adapter that turns each row into
 * the same idempotent capture command a manual entry would use.
 */
class FuelImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private static final String HEADER = "providerTransactionId,vehicleId,driverId,occurredAt,vendorReference,"
            + "fuelProduct,quantity,quantityUnit,unitPrice,currency,odometerReading";

    private FuelTestDoubles.InMemoryFuelRepository repository;
    private FuelImportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new FuelTestDoubles.InMemoryFuelRepository();
        var driverScopes = new DriverScopeResolver(new FleetTestDoubles.InMemoryDriverProfileRepository(),
                new FleetAccessPolicy());
        var fuelApp = new FuelApplicationService(repository, new FuelTestDoubles.StubFleetReferencePort(),
                new FuelAccessPolicy(), new FleetTestDoubles.RecordingAuditPort(clock),
                new FleetTestDoubles.RecordingEventPublisher(), new FleetTestDoubles.InMemoryIdempotencyPort(), clock,
                new FleetTestDoubles.RecordingNotificationPort(), new FuelTestDoubles.RecordingFinanceAuditPort(),
                new FuelTestDoubles.StubOutboxAdminPort(), driverScopes, new FuelTestDoubles.InMemoryEvidencePort());
        service = new FuelImportService(fuelApp, new FuelAccessPolicy(), repository, clock);
    }

    @Test
    @DisplayName("every well-formed row is captured and the batch is marked completed")
    void importCsv_captures_every_well_formed_row() {
        String csv = HEADER + "\n" + row("PTX-1") + "\n" + row("PTX-2");

        var result = service.importCsv(SITE, "MANUAL_UPLOAD", "batch.csv", csv.getBytes(StandardCharsets.UTF_8),
                manager());

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.acceptedRows()).isEqualTo(2);
        assertThat(result.rejectedRows()).isEqualTo(0);
        assertThat(repository.transactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a row that fails a capture rule is rejected without failing the rest of the batch")
    void importCsv_rejects_an_individual_bad_row_without_failing_the_batch() {
        String badRow = "PTX-BAD,,," + "2026-07-21T07:00:00Z,GOIL,PETROL,40,LITRE,15,GHS,30";
        String csv = HEADER + "\n" + row("PTX-1") + "\n" + badRow;

        var result = service.importCsv(SITE, "MANUAL_UPLOAD", "batch.csv", csv.getBytes(StandardCharsets.UTF_8),
                manager());

        assertThat(result.acceptedRows()).isEqualTo(1);
        assertThat(result.rejectedRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("importing is denied to an actor without FUEL_TRANSACTION_IMPORT")
    void importCsv_is_denied_without_import_permission() {
        var driver = FuelTestDoubles.driver("driver@clet.edu.gh", SITE);
        String csv = HEADER + "\n" + row("PTX-1");

        assertThatThrownBy(() -> service.importCsv(SITE, "MANUAL_UPLOAD", "batch.csv",
                csv.getBytes(StandardCharsets.UTF_8), driver))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("re-importing the same file content for the same site and source is refused")
    void importCsv_duplicate_file_content_is_refused() {
        String csv = HEADER + "\n" + row("PTX-1");
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);
        service.importCsv(SITE, "MANUAL_UPLOAD", "batch.csv", content, manager());

        assertThatThrownBy(() -> service.importCsv(SITE, "MANUAL_UPLOAD", "batch-again.csv", content, manager()))
                .isInstanceOf(FuelImportAlreadyProcessedException.class);
    }

    private static String row(String providerTransactionId) {
        return providerTransactionId + "," + java.util.UUID.randomUUID() + "," + java.util.UUID.randomUUID()
                + ",2026-07-21T07:00:00Z,GOIL,PETROL,40,LITRE,15,GHS,30";
    }

    private static ActorContext manager() {
        return FuelTestDoubles.fuelManager(SITE);
    }
}
