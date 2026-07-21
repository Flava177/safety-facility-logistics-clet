package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S166-03: "Audit records shall be append-only and tamper-evident using a hash-chain or
 * equivalent control" and "Given an audit chain replay detects tampering, when the integrity check
 * runs, then the system raises a critical compliance alert."
 */
class AuditHashChainTest {

    private static final Instant T0 = Instant.parse("2026-07-21T08:00:00Z");

    @Test
    @DisplayName("the first record links to the genesis hash")
    void first_record_links_to_genesis() {
        AuditEvent sealed = AuditHashChain.seal(event(0), 0, null);

        assertThat(sealed.previousHash()).isEqualTo(AuditHashChain.GENESIS_HASH);
        assertThat(sealed.recordHash()).hasSize(64).isNotEqualTo(AuditHashChain.GENESIS_HASH);
        assertThat(sealed.isSealed()).isTrue();
    }

    @Test
    @DisplayName("each record links to its predecessor and an intact chain verifies")
    void intact_chain_verifies() {
        List<AuditEvent> chain = chainOf(5);

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isTrue();
        assertThat(verification.recordsChecked()).isEqualTo(5);
        assertThat(verification.headHash()).isEqualTo(chain.get(4).recordHash());
        assertThat(verification.firstDivergentSequence()).isNull();
    }

    @Test
    @DisplayName("an empty chain verifies against the genesis hash")
    void empty_chain_verifies() {
        AuditChainVerification verification = AuditHashChain.verify(List.of(), AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isTrue();
        assertThat(verification.headHash()).isEqualTo(AuditHashChain.GENESIS_HASH);
    }

    @Test
    @DisplayName("hashing is deterministic for the same content")
    void hashing_is_deterministic() {
        AuditEvent event = event(3);

        assertThat(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, event))
                .isEqualTo(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, event));
    }

    @Test
    @DisplayName("a mutated record body is detected at its sequence number")
    void mutated_record_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(5));
        AuditEvent original = chain.get(2);
        // Somebody edits the stored "after" image but leaves the stored hash untouched.
        chain.set(2, new AuditEvent(original.id(), original.sequenceNo(), original.siteScope(), original.actorId(),
                original.actorDisplayName(), original.action(), original.resourceType(), original.resourceId(),
                original.beforeValue(), "{\"lifecycleStatus\":\"ACTIVE\"}", original.correlationId(),
                original.sourceChannel(), original.occurredAt(), original.previousHash(), original.recordHash()));

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.firstDivergentSequence()).isEqualTo(2L);
        assertThat(verification.reason()).contains("Record hash does not match");
    }

    @Test
    @DisplayName("a removed record breaks the sequence")
    void removed_record_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(5));
        chain.remove(2);

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.firstDivergentSequence()).isEqualTo(2L);
        assertThat(verification.reason()).contains("not contiguous");
    }

    @Test
    @DisplayName("a reordered record breaks the previous-hash link")
    void reordered_record_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(5));
        // Swap the bodies of records 2 and 3 while keeping the sequence numbers in order, which is what a
        // targeted tamper attempt looks like.
        AuditEvent second = chain.get(2);
        AuditEvent third = chain.get(3);
        chain.set(2, withSequence(third, 2));
        chain.set(3, withSequence(second, 3));

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.firstDivergentSequence()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a record whose previous-hash link was rewritten is detected")
    void rewritten_link_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(4));
        AuditEvent original = chain.get(3);
        chain.set(3, new AuditEvent(original.id(), original.sequenceNo(), original.siteScope(), original.actorId(),
                original.actorDisplayName(), original.action(), original.resourceType(), original.resourceId(),
                original.beforeValue(), original.afterValue(), original.correlationId(), original.sourceChannel(),
                original.occurredAt(), AuditHashChain.GENESIS_HASH, original.recordHash()));

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.firstDivergentSequence()).isEqualTo(3L);
        assertThat(verification.reason()).contains("Previous-hash link");
    }

    private static List<AuditEvent> chainOf(int size) {
        List<AuditEvent> chain = new ArrayList<>();
        String previousHash = AuditHashChain.GENESIS_HASH;
        for (int index = 0; index < size; index++) {
            AuditEvent sealed = AuditHashChain.seal(event(index), index, previousHash);
            chain.add(sealed);
            previousHash = sealed.recordHash();
        }
        return chain;
    }

    private static AuditEvent withSequence(AuditEvent event, long sequenceNo) {
        return new AuditEvent(event.id(), sequenceNo, event.siteScope(), event.actorId(), event.actorDisplayName(),
                event.action(), event.resourceType(), event.resourceId(), event.beforeValue(), event.afterValue(),
                event.correlationId(), event.sourceChannel(), event.occurredAt(), event.previousHash(),
                event.recordHash());
    }

    private static AuditEvent event(int index) {
        return AuditEvent.unsealed(
                UUID.nameUUIDFromBytes(("fleet-audit-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                SiteCode.of("ACCRA"), "officer@clet.edu.gh", "Fleet Officer", AuditAction.UPDATE, "Vehicle",
                "GT-" + index + "-26", "{\"status\":\"ACTIVE\"}", "{\"status\":\"INACTIVE\"}",
                "corr-" + index, SourceChannel.WEB, T0.plusSeconds(index * 60L));
    }
}
