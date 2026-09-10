package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelCard;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@code JdbcFuelRepository.savePolicy} and {@code saveCard} against a real PostgreSQL.
 *
 * <p>{@code savePolicy} used to be an unconditional {@code ON CONFLICT DO UPDATE}: it bumped
 * {@code version} without ever checking it, so two concurrent edits both won and the second's write
 * silently erased the first's. {@code saveCard} checked the version but, on a conflict, fell through
 * to {@code return c;} - the caller's own stale argument, echoed back as if it had saved. Both are the
 * same class of bug this session's facilities-service work fixed, reached here via raw JDBC UPSERT
 * rather than a broken JPA mapping. This pins the fix against the real adapter.
 */
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FleetPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.fuel.scheduling.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false", "sfl.fleet.messaging.transport=local"})
class JdbcFuelRepositoryOptimisticLockingTest extends FleetPostgresSupport {

    @Autowired
    private FuelRepository repository;

    private static String site() {
        return "FL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    // ---- FuelPolicy ---------------------------------------------------------------------------

    @Test
    void policy_insert_succeeds_and_is_not_mistaken_for_a_conflict() {
        FuelPolicy policy = newPolicy(site(), "V1");

        FuelPolicy saved = repository.savePolicy(policy);

        assertThat(saved.metadata().version()).isZero();
        assertThat(repository.findPolicy(policy.id())).isPresent();
    }

    @Test
    void policy_update_succeeds_and_advances_the_version() {
        FuelPolicy created = repository.savePolicy(newPolicy(site(), "V1"));

        FuelPolicy updated = repository.savePolicy(edited(created, "V2"));

        assertThat(updated.name()).isEqualTo("V2");
        assertThat(updated.metadata().version()).isEqualTo(1L);
    }

    @Test
    void a_policy_write_built_on_a_superseded_read_is_rejected_and_the_winning_write_survives() {
        FuelPolicy created = repository.savePolicy(newPolicy(site(), "V1"));
        // Two editors both read the same (version 0) row and build their edits from it.
        FuelPolicy firstEditorsEdit = edited(created, "WINNER");
        FuelPolicy secondEditorsEdit = edited(created, "LOSER");

        FuelPolicy winner = repository.savePolicy(firstEditorsEdit);
        assertThat(winner.name()).isEqualTo("WINNER");

        assertThatThrownBy(() -> repository.savePolicy(secondEditorsEdit))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The database still holds the winner's data, not a merge and not the loser's.
        FuelPolicy persisted = repository.findPolicy(created.id()).orElseThrow();
        assertThat(persisted.name()).isEqualTo("WINNER");
        assertThat(persisted.metadata().version()).isEqualTo(1L);
    }

    private static FuelPolicy newPolicy(String site, String name) {
        Instant now = Instant.now();
        return new FuelPolicy(UUID.randomUUID(), SiteCode.of(site), name, now.minusSeconds(60), null, 1,
                new BigDecimal("500"), new BigDecimal("1000"), new BigDecimal("20000"), null, null, null, 0,
                true, 24, new BigDecimal("400"), 8, Set.of("DIESEL"), Set.of("CLET STATION"),
                FuelPolicy.Status.ACTIVE,
                RecordMetadata.createdBy("tester", now, SourceChannel.WEB, "corr-policy-lock-test"));
    }

    /** A revision built from {@code existing}'s own read - the fleet-logistics convention: an edit
     * carries the version it was read at, unchanged, and the database owns the bump. */
    private static FuelPolicy edited(FuelPolicy existing, String newName) {
        return new FuelPolicy(existing.id(), existing.siteCode(), newName, existing.effectiveFrom(),
                existing.effectiveTo(), existing.policyVersion(), existing.maxPerTransaction(),
                existing.dailyLimit(), existing.monthlyLimit(), existing.tankCapacity(),
                existing.minConsumption(), existing.maxConsumption(), existing.odometerJumpTolerance(),
                existing.receiptRequired(), existing.receiptGraceHours(), existing.materialityAmount(),
                existing.anomalySlaHours(), existing.allowedFuelProducts(), existing.approvedVendors(),
                existing.status(),
                existing.metadata().modifiedBy("tester", Instant.now(), SourceChannel.WEB, "corr-edit"));
    }

    // ---- FuelCard -------------------------------------------------------------------------------

    @Test
    void card_insert_succeeds_and_is_not_mistaken_for_a_conflict() {
        FuelCard card = newCard(site());

        FuelCard saved = repository.saveCard(card);

        assertThat(saved.metadata().version()).isZero();
        assertThat(repository.findCard(card.id())).isPresent();
    }

    @Test
    void card_update_succeeds_and_advances_the_version() {
        FuelCard created = repository.saveCard(newCard(site()));

        FuelCard updated = repository.saveCard(created.suspend("Lost",
                created.metadata().modifiedBy("tester", Instant.now(), SourceChannel.WEB, "corr-suspend")));

        assertThat(updated.status()).isEqualTo(FuelCard.Status.SUSPENDED);
        assertThat(updated.metadata().version()).isEqualTo(1L);
    }

    @Test
    void a_card_write_built_on_a_superseded_read_is_rejected_and_the_winning_write_survives() {
        FuelCard created = repository.saveCard(newCard(site()));
        RecordMetadata basis = created.metadata()
                .modifiedBy("tester", Instant.now(), SourceChannel.WEB, "corr-edit");
        FuelCard firstEditorsEdit = created.suspend("Lost card", basis);
        FuelCard secondEditorsEdit = created.suspend("Different reason", basis);

        FuelCard winner = repository.saveCard(firstEditorsEdit);
        assertThat(winner.suspensionReason()).isEqualTo("Lost card");

        assertThatThrownBy(() -> repository.saveCard(secondEditorsEdit))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The database still holds the winner's data - not the loser's, and no stale echo of either
        // caller's own in-memory object.
        FuelCard persisted = repository.findCard(created.id()).orElseThrow();
        assertThat(persisted.suspensionReason()).isEqualTo("Lost card");
        assertThat(persisted.metadata().version()).isEqualTo(1L);
    }

    private static FuelCard newCard(String site) {
        Instant now = Instant.now();
        String masked = "****" + (1000 + Math.abs(UUID.randomUUID().hashCode() % 9000));
        return FuelCard.issue(UUID.randomUUID(), SiteCode.of(site), masked, "CLET FUEL CARDS", null, null,
                LocalDate.now().minusDays(7), null, null, null, null, null,
                RecordMetadata.createdBy("tester", now, SourceChannel.WEB, "corr-card-lock-test"));
    }
}
