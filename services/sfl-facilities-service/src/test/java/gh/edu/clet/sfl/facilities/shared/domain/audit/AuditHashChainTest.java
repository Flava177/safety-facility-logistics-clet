package gh.edu.clet.sfl.facilities.shared.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tamper detection by replay (SRS-SFL-S152-03).
 *
 * <p>The chain is only worth building if a mutated, removed or reordered record is detectable. Each
 * of those three is asserted below.
 */
class AuditHashChainTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");

    @Test
    void an_intact_chain_replays_clean() {
        List<AuditEvent> chain = chainOf(5);

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isTrue();
        assertThat(verification.recordsVerified()).isEqualTo(5);
        assertThat(verification.headHash()).isEqualTo(chain.get(4).recordHash());
    }

    @Test
    void an_empty_chain_is_intact() {
        AuditChainVerification verification = AuditHashChain.verify(List.of(), AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isTrue();
        assertThat(verification.recordsVerified()).isZero();
    }

    @Test
    void a_mutated_record_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(4));
        AuditEvent original = chain.get(2);
        // Somebody edits the after-value in the database, leaving the hashes as they were.
        chain.set(2, new AuditEvent(original.id(), original.sequenceNo(), original.siteScope(),
                original.actorId(), original.actorDisplayName(), original.action(), original.resourceType(),
                original.resourceId(), original.beforeValue(), "{\"tampered\":true}", original.correlationId(),
                original.sourceChannel(), original.occurredAt(), original.previousHash(),
                original.recordHash()));

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.brokenAtSequence()).isEqualTo(2L);
        assertThat(verification.reason()).isEqualTo("Record hash does not match the stored content");
    }

    @Test
    void a_removed_record_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(4));
        chain.remove(2);

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.reason()).isEqualTo("Audit sequence is not contiguous");
    }

    @Test
    void a_reordered_pair_is_detected() {
        List<AuditEvent> chain = new ArrayList<>(chainOf(4));
        AuditEvent second = chain.get(1);
        chain.set(1, chain.get(2));
        chain.set(2, second);

        AuditChainVerification verification = AuditHashChain.verify(chain, AuditHashChain.GENESIS_HASH);

        assertThat(verification.intact()).isFalse();
    }

    @Test
    void the_first_record_links_to_the_genesis_hash() {
        AuditEvent first = AuditHashChain.seal(event(0), 0L, null);

        assertThat(first.previousHash()).isEqualTo(AuditHashChain.GENESIS_HASH);
        assertThat(first.recordHash()).hasSize(64);
    }

    @Test
    void an_absent_field_and_an_empty_one_hash_differently() {
        AuditEvent absent = AuditEvent.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "MAIN",
                "actor", "Actor", AuditAction.SITE_CREATED, "Site", "s1", null, "after", "corr",
                SourceChannel.WEB, NOW);
        AuditEvent empty = AuditEvent.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "MAIN",
                "actor", "Actor", AuditAction.SITE_CREATED, "Site", "s1", "", "after", "corr",
                SourceChannel.WEB, NOW);

        assertThat(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, absent))
                .isNotEqualTo(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, empty));
    }

    @Test
    void the_same_record_always_hashes_the_same() {
        AuditEvent event = event(7);

        assertThat(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, event))
                .isEqualTo(AuditHashChain.computeHash(AuditHashChain.GENESIS_HASH, event));
    }

    private static List<AuditEvent> chainOf(int size) {
        List<AuditEvent> chain = new ArrayList<>();
        String previous = AuditHashChain.GENESIS_HASH;
        for (int i = 0; i < size; i++) {
            AuditEvent sealed = AuditHashChain.seal(event(i), i, previous);
            chain.add(sealed);
            previous = sealed.recordHash();
        }
        return chain;
    }

    private static AuditEvent event(int index) {
        return AuditEvent.of(UUID.nameUUIDFromBytes(("event-" + index).getBytes()), "MAIN", "officer",
                "Facilities Officer", AuditAction.ROOM_UPDATED, "FacilityRoom", "room-" + index,
                "{\"before\":" + index + "}", "{\"after\":" + index + "}", "corr-" + index,
                SourceChannel.WEB, NOW.plusSeconds(index));
    }
}
