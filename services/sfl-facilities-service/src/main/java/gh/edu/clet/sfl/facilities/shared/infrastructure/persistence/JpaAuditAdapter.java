package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditHashChain;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the hash-chained audit trail (SRS-SFL-S152-03).
 *
 * <p>Appends run in the caller's transaction — the requirement asks for the operational record and
 * its audit entry "in the same unit of work where possible", so a rolled-back change takes its audit
 * record with it.
 *
 * <p>{@link #recordDenial} is the exception: it runs {@link Propagation#REQUIRES_NEW} because a denial
 * is always followed by a thrown exception, and an audit record that rolls back with the refusal it
 * documents would leave no trace of the attempt at all.
 */
@Component
class JpaAuditAdapter implements AuditPort {

    private final AuditRecordRepository records;
    private final AuditChainStateRepository chainState;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JpaAuditAdapter(AuditRecordRepository records, AuditChainStateRepository chainState,
            ObjectMapper objectMapper, Clock clock) {
        this.records = records;
        this.chainState = chainState;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(ActorContext actor, SourceChannel channel, AuditAction action, String resourceType,
            String resourceId, String siteScope, Object before, Object after) {
        return append(AuditEvent.of(UUID.randomUUID(), scope(siteScope), actor.actorId(),
                actor.principal().displayName(), action, resourceType, resourceId,
                writeJson(before), writeJson(after), actor.correlationId(), channel, now()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordDenial(ActorContext actor, SourceChannel channel, String resourceType,
            String resourceId, String siteScope, String reason) {
        return append(AuditEvent.of(UUID.randomUUID(), scope(siteScope), actor.actorId(),
                actor.principal().displayName(), AuditAction.AUTHORIZATION_DENIED, resourceType,
                resourceId == null || resourceId.isBlank() ? "-" : resourceId,
                null, writeJson(new Denial(reason, actor.principal().siteScopes())),
                actor.correlationId(), channel, now()));
    }

    /**
     * The timestamp an audit record is stamped and hashed with, truncated to microseconds.
     *
     * <p>PostgreSQL {@code timestamptz} stores microsecond precision; a Java {@link Instant} carries
     * nanoseconds. The record hash is computed over {@code occurredAt.toString()}, so an untruncated
     * instant is hashed at nanosecond precision and read back at microsecond precision — and every
     * record replays as tampered. Truncating before hashing makes what is stored and what is hashed
     * the same value. Found by replaying the chain against a real database (SRS-SFL-S152-03).
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * The filtered audit search.
     *
     * <p>Built as a specification so an absent filter contributes no predicate at all. Binding a typed
     * {@code null} into a {@code (:param is null or column = :param)} clause is what PostgreSQL rejects
     * with "could not determine data type of parameter"; omitting the clause sidesteps it entirely.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> search(String siteScope, String resourceType, String resourceId, String actorId,
            AuditAction action, Instant from, Instant to, int limit) {
        Specification<AuditRecordEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            equalIfPresent(builder, predicates, root.get("siteScope"), blankToNull(siteScope));
            equalIfPresent(builder, predicates, root.get("resourceType"), blankToNull(resourceType));
            equalIfPresent(builder, predicates, root.get("resourceId"), blankToNull(resourceId));
            equalIfPresent(builder, predicates, root.get("actorId"), blankToNull(actorId));
            equalIfPresent(builder, predicates, root.get("action"), action);
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return predicates.isEmpty() ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };

        return records
                .findAll(specification, PageRequest.of(0, Math.max(1, Math.min(limit, 500)),
                        Sort.by(Sort.Direction.DESC, "sequenceNo")))
                .stream()
                .map(AuditRecordEntity::toDomain)
                .toList();
    }

    private static void equalIfPresent(CriteriaBuilder builder, List<Predicate> predicates,
            Path<?> path, Object value) {
        if (value != null) {
            predicates.add(builder.equal(path, value));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuditChainVerification verifyChain() {
        List<AuditEvent> ordered = records.findAllByOrderBySequenceNoAsc().stream()
                .map(AuditRecordEntity::toDomain)
                .toList();
        return AuditHashChain.verify(ordered, AuditHashChain.GENESIS_HASH);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> historyFor(String resourceType, UUID resourceId) {
        return records.findByResourceTypeAndResourceIdOrderBySequenceNoAsc(resourceType, resourceId.toString())
                .stream()
                .map(AuditRecordEntity::toDomain)
                .toList();
    }

    private AuditEvent append(AuditEvent event) {
        AuditChainStateEntity head = chainState.lockHead()
                .orElseThrow(() -> new IllegalStateException(
                        "Audit chain head row is missing; migration V5 did not seed facility_audit_chain_state"));
        AuditEvent sealed = AuditHashChain.seal(event, head.nextSequence(), head.headHash());
        records.save(AuditRecordEntity.from(sealed));
        head.advance(sealed.recordHash(), clock.instant());
        chainState.save(head);
        return sealed;
    }

    /**
     * A denial's payload.
     *
     * <p>The actor's scopes are recorded alongside the reason because "not authorised" is only useful
     * to an investigator next to what the actor *was* authorised for.
     */
    private record Denial(String reason, java.util.Set<String> actorSiteScopes) {
    }

    private static String scope(String siteScope) {
        return siteScope == null || siteScope.isBlank() ? "*" : siteScope;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the audit payload", exception);
        }
    }
}
