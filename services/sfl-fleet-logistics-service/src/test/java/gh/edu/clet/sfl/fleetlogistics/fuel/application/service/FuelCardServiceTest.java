package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelCard;
import gh.edu.clet.sfl.fleetlogistics.fuel.support.FuelTestDoubles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S168fuel-04 fuel-card register - issuing, assignment and lifecycle. */
class FuelCardServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private FuelTestDoubles.InMemoryFuelRepository repository;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FuelCardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new FuelTestDoubles.InMemoryFuelRepository();
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        service = new FuelCardService(repository, new FuelAccessPolicy(), audit, clock);
    }

    @Test
    @DisplayName("a manager issues an active card, audited and readable back")
    void issue_persists_an_active_card() {
        FuelCard card = service.issue(issueCommand("****1234", FuelTestDoubles.fuelManager(SITE)));

        assertThat(card.status()).isEqualTo(FuelCard.Status.ACTIVE);
        assertThat(service.card(card.id(), FuelTestDoubles.fuelManager(SITE)).maskedReference())
                .isEqualTo("****1234");
        assertThat(audit.hasRecord(gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction.CREATE, "FuelCard"))
                .isTrue();
    }

    @Test
    @DisplayName("a logistics officer, who only reads cards, may not issue one")
    void issue_is_denied_to_an_actor_without_card_manage_permission() {
        var officer = FuelTestDoubles.fuelOfficer(SITE);

        assertThatThrownBy(() -> service.issue(issueCommand("****5678", officer)))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("issuing a second live card against a reference already live at the site is refused")
    void issue_duplicate_live_reference_is_refused() {
        service.issue(issueCommand("****9999", FuelTestDoubles.fuelManager(SITE)));

        assertThatThrownBy(() -> service.issue(issueCommand("****9999", FuelTestDoubles.fuelManager(SITE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("suspending a card without a reason is refused")
    void suspend_without_reason_is_refused() {
        FuelCard card = service.issue(issueCommand("****4321", FuelTestDoubles.fuelManager(SITE)));

        var suspend = new FuelCardService.TransitionCard(card.id(), "suspend", null, null, null,
                FuelTestDoubles.fuelManager(SITE), SourceChannel.WEB);

        assertThatThrownBy(() -> service.transition(suspend)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a suspended card can be reinstated, and the transition is audited")
    void suspend_then_reinstate_round_trips_the_card_to_active() {
        FuelCard card = service.issue(issueCommand("****7777", FuelTestDoubles.fuelManager(SITE)));
        service.transition(new FuelCardService.TransitionCard(card.id(), "suspend", "lost card", null, null,
                FuelTestDoubles.fuelManager(SITE), SourceChannel.WEB));

        FuelCard reinstated = service.transition(new FuelCardService.TransitionCard(card.id(), "reinstate", null,
                null, null, FuelTestDoubles.fuelManager(SITE), SourceChannel.WEB));

        assertThat(reinstated.status()).isEqualTo(FuelCard.Status.ACTIVE);
    }

    private FuelCardService.IssueCard issueCommand(String reference, ActorContext actor) {
        return new FuelCardService.IssueCard(SITE, reference, "PROVIDER", UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), null, BigDecimal.valueOf(200), BigDecimal.valueOf(2000), null, null,
                actor, SourceChannel.WEB);
    }
}
