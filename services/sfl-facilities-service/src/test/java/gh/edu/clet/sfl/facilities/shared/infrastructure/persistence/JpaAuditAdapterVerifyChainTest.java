package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditHashChain;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code verifyChain} replaying in bounded pages instead of loading the whole append-only table.
 *
 * <p>Uses a mocked {@link AuditRecordRepository} that answers the same keyset-paginated query the real
 * one would, so this pins the adapter's batching/carry-forward-previous-hash/cap logic without a real
 * database: that logic is what changed, not the SQL.
 */
class JpaAuditAdapterVerifyChainTest {

    private final AuditRecordRepository records = mock(AuditRecordRepository.class);
    private final AuditChainStateRepository chainState = mock(AuditChainStateRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final Clock clock = Clock.systemUTC();

    private JpaAuditAdapter adapter(int batchSize, int maxBatchesPerCall) {
        return new JpaAuditAdapter(records, chainState, objectMapper, clock, batchSize, maxBatchesPerCall);
    }

    /** A valid chain of {@code length} sealed records, starting at genesis. */
    private static List<AuditRecordEntity> chain(int length) {
        List<AuditRecordEntity> entities = new ArrayList<>();
        String previousHash = AuditHashChain.GENESIS_HASH;
        for (long sequence = 0; sequence < length; sequence++) {
            AuditEvent unsealed = AuditEvent.of(UUID.randomUUID(), "MAIN", "tester", "Tester",
                    AuditAction.SITE_UPDATED, "Site", "site-" + sequence, null, "{}", "corr-" + sequence,
                    SourceChannel.WEB, Instant.EPOCH.plusSeconds(sequence));
            AuditEvent sealed = AuditHashChain.seal(unsealed, sequence, previousHash);
            entities.add(AuditRecordEntity.from(sealed));
            previousHash = sealed.recordHash();
        }
        return entities;
    }

    private void stubKeysetPaging(List<AuditRecordEntity> entities) {
        when(records.findBySequenceNoGreaterThanOrderBySequenceNoAsc(anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    long after = invocation.getArgument(0);
                    Pageable pageable = invocation.getArgument(1);
                    return entities.stream()
                            .filter(entity -> entity.toDomain().sequenceNo() > after)
                            .sorted(Comparator.comparingLong(entity -> entity.toDomain().sequenceNo()))
                            .limit(pageable.getPageSize())
                            .toList();
                });
    }

    @Test
    void a_chain_within_the_bound_verifies_to_completion_in_one_call() {
        List<AuditRecordEntity> entities = chain(12);
        stubKeysetPaging(entities);

        AuditChainVerification result = adapter(5, 200).verifyChain();

        assertThat(result.intact()).isTrue();
        assertThat(result.complete()).isTrue();
        assertThat(result.recordsVerified()).isEqualTo(12);
        assertThat(result.resumeFromSequence()).isNull();
        // Pages of 5, 5, 2, then one empty page confirming the chain has no more records - never all
        // twelve records loaded by one query.
        verify(records, times(4)).findBySequenceNoGreaterThanOrderBySequenceNoAsc(anyLong(), any());
    }

    @Test
    void a_chain_longer_than_the_bound_stops_early_without_finding_a_break_and_can_be_resumed() {
        List<AuditRecordEntity> entities = chain(12);
        stubKeysetPaging(entities);

        // batchSize 5 x maxBatches 2 = a 10-record bound, on a 12-record chain.
        AuditChainVerification result = adapter(5, 2).verifyChain();

        assertThat(result.intact()).isTrue();
        assertThat(result.complete()).isFalse();
        assertThat(result.recordsVerified()).isEqualTo(10);
        // A diagnostic only - verifyChain() itself always restarts from genesis on the next call rather
        // than persisting this as a checkpoint, so it names where this pass stopped, not a cursor this
        // adapter will honour on its own.
        assertThat(result.resumeFromSequence()).isEqualTo(10L);
    }

    @Test
    void a_tampered_record_past_the_first_page_is_still_caught_with_an_accurate_verified_count() {
        List<AuditRecordEntity> entities = new ArrayList<>(chain(8));
        // Corrupt the record at sequence 6 (second page, given batch size 5): its stored hash no
        // longer matches what recomputing it from its content and previous-hash would produce - the
        // same shape of tamper a direct database edit would leave behind.
        AuditEvent original = entities.get(6).toDomain();
        AuditEvent corrupted = original.sealed(original.sequenceNo(), original.previousHash(),
                "f".repeat(64));
        entities.set(6, AuditRecordEntity.from(corrupted));
        stubKeysetPaging(entities);

        AuditChainVerification result = adapter(5, 200).verifyChain();

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(6L);
        // Sequences 0-4 (page one) and 5 (page two, before the break) verified clean; the break is
        // found on sequence 6 itself, which is not counted as verified.
        assertThat(result.recordsVerified()).isEqualTo(6);
    }
}
