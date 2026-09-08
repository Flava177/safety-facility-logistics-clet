package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.support.DispatchTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S171-02 dispatch manifest lifecycle - create, add items, seal, dispatch. */
class DispatchManifestServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchManifestService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        service = new DispatchManifestService(repository, new DispatchAccessPolicy(),
                new DispatchTestDoubles.StubFleetReferencePort(), new FleetTestDoubles.RecordingAuditPort(clock),
                new FleetTestDoubles.RecordingEventPublisher(), clock);
    }

    @Test
    @DisplayName("a manifest is created in draft, an item is added, and the manifest can then be sealed")
    void create_addItem_and_seal_happy_path() {
        Dispatch created = service.createManifest(createManifest());
        assertThat(created.status()).isEqualTo(Dispatch.Status.DRAFT);

        registerItem(created.siteCode().value(), "ITM-1");
        DispatchManifestItem line = service.addItem(new DispatchManifestService.AddManifestItem(created.id(),
                itemId, "SEAL-1", 1, DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB));
        assertThat(line.expectedQuantity()).isEqualTo(1);

        Dispatch sealed = service.seal(created.id(), List.of("SEAL-1"), DispatchTestDoubles.dispatchController(SITE),
                SourceChannel.WEB);
        assertThat(sealed.status()).isEqualTo(Dispatch.Status.SEALED);
        assertThat(sealed.itemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("creating a manifest is refused to an actor without DISPATCH_MANIFEST_CREATE")
    void createManifest_is_denied_without_manifest_create_permission() {
        var securityOfficer = DispatchTestDoubles.securityOfficer(SITE);
        var command = new DispatchManifestService.CreateManifest(SITE, null, "Route 1", "handler-1", "Centre 1",
                null, null, null, null, securityOfficer, SourceChannel.WEB);

        assertThatThrownBy(() -> service.createManifest(command)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a manifest with no items cannot be sealed")
    void seal_with_no_items_is_refused() {
        Dispatch created = service.createManifest(createManifest());

        assertThatThrownBy(() -> service.seal(created.id(), List.of("SEAL-1"),
                DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no items");
    }

    @Test
    @DisplayName("an item can only be added while the manifest is still a draft")
    void addItem_after_sealing_is_refused() {
        Dispatch created = service.createManifest(createManifest());
        registerItem(created.siteCode().value(), "ITM-1");
        service.addItem(new DispatchManifestService.AddManifestItem(created.id(), itemId, "SEAL-1", 1,
                DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB));
        service.seal(created.id(), List.of("SEAL-1"), DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB);

        registerItem(SITE, "ITM-2");
        var command = new DispatchManifestService.AddManifestItem(created.id(), itemId, "SEAL-2", 1,
                DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB);

        assertThatThrownBy(() -> service.addItem(command)).isInstanceOf(IllegalStateException.class);
    }

    private UUID itemId;

    private void registerItem(String site, String itemNumber) {
        itemId = UUID.randomUUID();
        repository.saveItem(new gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem(itemId, itemNumber,
                gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode.of(site),
                gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem.Direction.OUTBOUND,
                gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem.Type.ORDINARY_MAIL,
                gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem.Sensitivity.ORDINARY, false,
                "Warehouse", "Centre 1", "Registry", "Centre Manager", null,
                gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem.Status.RECEIVED, null, null, null,
                null, null, false, null,
                gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata.createdBy("clerk", NOW,
                        SourceChannel.WEB, "corr-test")));
    }

    private DispatchManifestService.CreateManifest createManifest() {
        return new DispatchManifestService.CreateManifest(SITE, null, "Route 1", "handler-1", "Centre 1", null, null,
                null, null, DispatchTestDoubles.dispatchController(SITE), SourceChannel.WEB);
    }
}
