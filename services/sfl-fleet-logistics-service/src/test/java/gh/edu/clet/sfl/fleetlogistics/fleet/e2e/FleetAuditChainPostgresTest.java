package gh.edu.clet.sfl.fleetlogistics.fleet.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The fleet hash chain, replayed against a real PostgreSQL.
 *
 * <p><strong>Why this test exists.</strong> `S152_Gap_And_Conflict_Report.md` §8a records two defects
 * that made the *facilities* audit chain replay as tampered, and explicitly warns that
 * `sfl-fleet-logistics-service` was likely to carry both, unchecked:
 *
 * <ul>
 *   <li><strong>D-04</strong> — audit payloads stored as {@code jsonb}. PostgreSQL normalises jsonb by
 *       reordering object keys, so the value read back was never the value hashed.</li>
 *   <li><strong>D-05</strong> — nanosecond timestamps hashed, microsecond timestamps stored.
 *       PostgreSQL keeps microseconds; the extra precision is lost on the round trip.</li>
 * </ul>
 *
 * <p>Fleet's existing chain-intact assertions are in {@code TripApplicationServiceTest} and
 * {@code VehicleApplicationServiceTest}, which use an in-memory {@code RecordingAuditPort} — and an
 * in-memory double round-trips nothing, so it cannot see either defect. The one end-to-end check,
 * {@code DispatchMandatoryScenariosEndToEndTest}, asserts {@code isNotNull()} rather than
 * {@code intact()}. So the warning had gone unanswered for four build passes: **no test replayed this
 * chain off a real database.**
 *
 * <p>What running it establishes: fleet does <em>not</em> carry D-04, because
 * {@code AuditRecordEntity.toDomain(ObjectMapper)} re-canonicalises the stored JSON through
 * {@code CanonicalJson} on read, which neutralises jsonb's key reordering. D-05 is settled by the
 * assertion below rather than by reasoning about clock precision, which is the only way to settle it —
 * whether the JVM clock hands out nanoseconds is a platform detail, and a chain that verifies on one
 * developer's machine and not another's is exactly the failure this pins down.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "sfl.fleet.scheduling.sla.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false",
        "sfl.fleet.scheduling.compliance.enabled=false",
        "sfl.fleet.scheduling.dashboard.enabled=false",
        "sfl.fleet.messaging.transport=local"
})
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FleetPostgresSupport.unavailableReason()")
class FleetAuditChainPostgresTest extends FleetPostgresSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private AuditPort audit;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("records written through the JPA adapter replay intact off a real database")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void the_chain_replays_intact_after_a_round_trip() {
        // A payload with keys deliberately out of alphabetical order and of differing lengths. jsonb
        // orders keys by length then bytes, so if the stored form were hashed directly this would be
        // the shape that breaks it — which is what D-04 was.
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("zebra", "last alphabetically, shortest but for one");
        before.put("a", 1);
        before.put("mediumKey", true);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("a", 2);

        String resourceId = "AUDIT-CHAIN-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID();
        writeRecords(resourceId, before, after);

        // Replays every record in the table from genesis, not only the ones just written — so this also
        // asserts that nothing already in the database has drifted.
        var verification = audit.verifyChain();

        assertThat(verification.intact())
                .as("fleet audit chain replayed off PostgreSQL: %s", verification)
                .isTrue();
    }

    /**
     * {@code JpaAuditAdapter.record} is {@code @Transactional(MANDATORY)} on purpose — an audit entry
     * commits or rolls back with the state change it describes, so it refuses to run on its own. The
     * test therefore supplies the transaction a real caller would, and commits it before replaying:
     * verifying inside the same transaction would read the chain through the write's own snapshot and
     * prove nothing about what was stored.
     */
    private void writeRecords(String resourceId, Map<String, Object> before, Map<String, Object> after) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                appendThree(resourceId, before, after));
    }

    private void appendThree(String resourceId, Map<String, Object> before, Map<String, Object> after) {
        ActorContext actor = new ActorContext(
                new SiteScopedPrincipal("audit-chain-probe", "Audit Chain Probe",
                        Set.of(SflRole.FLEET_MANAGER), Set.of("*"), false),
                "audit-chain-" + SEQUENCE.incrementAndGet());

        // Several in sequence: a single record cannot show a chain, only a hash.
        audit.record(actor, SourceChannel.WEB, SiteCode.of("AUDITSITE"), AuditAction.CREATE,
                "AuditChainProbe", resourceId, null, before);
        audit.record(actor, SourceChannel.WEB, SiteCode.of("AUDITSITE"), AuditAction.UPDATE,
                "AuditChainProbe", resourceId, before, after);
        audit.record(actor, SourceChannel.SYSTEM, SiteCode.of("AUDITSITE"), AuditAction.STATE_TRANSITION,
                "AuditChainProbe", resourceId, after, after);
    }
}
