package gh.edu.clet.sfl.fleetlogistics.fleet.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.UploadEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetEvidenceApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Two operations that had no test at all, and shipped broken because of it.
 *
 * <h2>Why these have to be Spring tests and not unit tests</h2>
 *
 * <p>Both defects were transaction-boundary defects, and a transaction boundary is drawn by a proxy
 * the Spring context creates. A unit test constructing the service with test doubles calls the
 * target object directly, so {@code @Transactional} is never consulted and
 * {@code Propagation.MANDATORY} on the audit adapter never has anything to object to - the test
 * passes on code that fails on every real request. {@code FleetIntegrationApplicationServiceTest}
 * demonstrates the point: it exercises {@code replay} and was green throughout.
 *
 * <p>So these run against the real context and a real database. That is the only arrangement in
 * which the annotation under test does anything.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.fuel.scheduling.enabled=false",
    "sfl.fleet.scheduling.outbox.enabled=false", "sfl.fleet.messaging.transport=local"})
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class EvidenceAccessAndPolicyLifecycleEndToEndTest extends FleetPostgresSupport {

    @Autowired FleetEvidenceApplicationService evidence;
    @Autowired FleetAuditService audit;
    @Autowired FuelApplicationService fuel;

    /** The smallest byte sequence that sniffs as a JPEG. */
    private static byte[] jpeg(String distinguisher) {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] tail = distinguisher.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] all = new byte[head.length + tail.length + 2];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        all[all.length - 2] = (byte) 0xFF;
        all[all.length - 1] = (byte) 0xD9;
        return all;
    }

    private static ActorContext actorFor(String site) {
        return new ActorContext(new SiteScopedPrincipal("evidence.manager", "Evidence Manager",
                Set.of(SflRole.FLEET_MANAGER), Set.of(site), false), "evidence-lifecycle-e2e");
    }

    @Test
    @DisplayName("recording access to evidence writes the audit entry instead of failing with no transaction")
    void record_access_runs_inside_a_transaction() {
        String site = "ACCESS" + System.nanoTime();
        ActorContext actor = actorFor(site);

        EvidenceReference stored = evidence.upload(new UploadEvidence(site, "Vehicle",
                UUID.randomUUID().toString(), "FUEL_RECEIPT", "receipt.jpg", "image/jpeg",
                jpeg("record-access"), EvidenceRetentionClass.COMPLIANCE_7_YEARS, null, actor,
                SourceChannel.WEB));

        // Before the fix this threw IllegalTransactionStateException - "No existing transaction found
        // for transaction marked with propagation 'mandatory'" - and the caller saw a bare 500 with a
        // correlation id and no explanation.
        assertThatCode(() -> evidence.recordAccess(stored.id(), actor, SourceChannel.WEB))
                .doesNotThrowAnyException();

        // The audit entry is the reason the method is transactional at all, so its presence is the
        // assertion that matters. "It did not throw" would also pass against a version that silently
        // skipped the write.
        var trail = audit.search(new AuditPort.AuditQuery(java.util.List.of(site), "EvidenceReference",
                stored.id().toString(), null, null, null, null, 0, 50));
        assertThat(trail).anyMatch(entry -> entry.action() == AuditAction.EVIDENCE_VIEWED);
    }

    @Test
    @DisplayName("a fuel policy can be revised, and the revision is what a later reconciliation reads")
    void policy_can_be_edited() {
        String site = "EDIT" + System.nanoTime();
        ActorContext actor = actorFor(site);
        Instant now = Instant.now();

        FuelPolicy created = fuel.createPolicy(new FuelApplicationService.CreatePolicy(site, "Original",
                now.minusSeconds(86400), null, 1, new BigDecimal("50"), null, null, new BigDecimal("80"),
                null, null, 500, true, 24, new BigDecimal("400"), 8, Set.of("DIESEL"),
                Set.of("CLET STATION"), actor, SourceChannel.WEB));

        FuelPolicy revised = fuel.updatePolicy(new FuelApplicationService.UpdatePolicy(created.id(),
                "Revised", created.effectiveFrom(), null, 2, new BigDecimal("65"), null, null,
                new BigDecimal("90"), null, null, 400, true, 12, new BigDecimal("500"), 6,
                new BigDecimal("0.25"), 720, 3, Set.of("DIESEL", "PETROL"),
                Set.of("CLET STATION", "GOIL"), actor, SourceChannel.WEB));

        // Same row - an edit, not a supersession. The persistence adapter was INSERT-only until this
        // existed, so before the upsert this failed on the primary key.
        assertThat(revised.id()).isEqualTo(created.id());
        assertThat(revised.name()).isEqualTo("Revised");
        assertThat(revised.policyVersion()).isEqualTo(2);
        assertThat(revised.maxPerTransaction()).isEqualByComparingTo("65");
        assertThat(revised.approvedVendors()).containsExactlyInAnyOrder("CLET STATION", "GOIL");
        // Creation provenance survives a revision: who created a policy does not change because
        // somebody later corrected its limits.
        assertThat(revised.metadata().createdBy()).isEqualTo(created.metadata().createdBy());

        // And it is the revision the register now returns, not a second row beside the original.
        assertThat(fuel.policy(created.id(), actor).name()).isEqualTo("Revised");
    }

    @Test
    @DisplayName("withdrawing a policy archives it, leaves it readable, and frees its period")
    void policy_can_be_withdrawn_without_stranding_the_runs_that_cited_it() {
        String site = "WDRAW" + System.nanoTime();
        ActorContext actor = actorFor(site);
        Instant now = Instant.now();

        FuelPolicy created = fuel.createPolicy(new FuelApplicationService.CreatePolicy(site, "Retiring",
                now.minusSeconds(86400), null, 1, new BigDecimal("50"), null, null, new BigDecimal("80"),
                null, null, 500, true, 24, new BigDecimal("400"), 8, Set.of("DIESEL"),
                Set.of("CLET STATION"), actor, SourceChannel.WEB));

        // An overlapping replacement is refused while the original is in force.
        assertThatThrownBy(() -> fuel.createPolicy(new FuelApplicationService.CreatePolicy(site,
                "Replacement", now.minusSeconds(86400), null, 1, new BigDecimal("50"), null, null,
                new BigDecimal("80"), null, null, 500, true, 24, new BigDecimal("400"), 8,
                Set.of("DIESEL"), Set.of("CLET STATION"), actor, SourceChannel.WEB)))
                .isInstanceOf(RuntimeException.class);

        FuelPolicy withdrawn = fuel.withdrawPolicy(created.id(), "Superseded by the 2027 rates", actor,
                SourceChannel.WEB);

        assertThat(withdrawn.status()).isEqualTo(FuelPolicy.Status.ARCHIVED);
        // Still readable. Every reconciliation run names the policy that judged it, so the row has to
        // survive - this is the difference between withdrawing and deleting.
        assertThat(fuel.policy(created.id(), actor).status()).isEqualTo(FuelPolicy.Status.ARCHIVED);
        // And it applies to nothing new, which is what releases the period.
        assertThat(withdrawn.appliesAt(now)).isFalse();

        assertThatCode(() -> fuel.createPolicy(new FuelApplicationService.CreatePolicy(site,
                "Replacement", now.minusSeconds(86400), null, 1, new BigDecimal("50"), null, null,
                new BigDecimal("80"), null, null, 500, true, 24, new BigDecimal("400"), 8,
                Set.of("DIESEL"), Set.of("CLET STATION"), actor, SourceChannel.WEB)))
                .doesNotThrowAnyException();
    }
}
