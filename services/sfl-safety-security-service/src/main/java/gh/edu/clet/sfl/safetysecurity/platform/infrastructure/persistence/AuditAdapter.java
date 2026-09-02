package gh.edu.clet.sfl.safetysecurity.platform.infrastructure.persistence;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Append-only, tamper-evident audit log for {@code safety_security}, one table shared by every
 * module in the schema rather than one per module.
 *
 * <p>Deliberately JDBC, matching S174's {@code JdbcAuditAdapter} in the same service, even though
 * {@code safety_security} is Hibernate's JPA default schema. An append-only chain table with no
 * update path and one write shape gets nothing from an {@code @Entity} mapping, and JDBC keeps the
 * hash computation and the insert in the same place, the way the S174 precedent already does.
 *
 * <p>Each row links to the previous via a hash over its scalar who/what/when + chain fields;
 * {@link #verifyChain()} replays and reports the first divergence, so a mutated row is detected.
 * Before/after images are stored as JSON for forensics.
 */
@Component
public class AuditAdapter implements AuditPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditAdapter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(ActorContext actor, String sourceChannel, String siteScope, String action,
            String resourceType, String resourceId, Object beforeValue, Object afterValue, String reason) {
        long sequence = nextSequence();
        String previousHash = jdbc.query(
                "SELECT record_hash FROM safety_security.audit_log WHERE sequence_no=?",
                rs -> rs.next() ? rs.getString(1) : null, sequence - 1);
        Instant occurredAt = clock.instant();
        String recordHash = hash(sequence, actor.actorId(), action, resourceType, resourceId, siteScope,
                sourceChannel, occurredAt, previousHash);
        jdbc.update("""
                INSERT INTO safety_security.audit_log (id,sequence_no,actor,action,resource_type,resource_id,
                    site_scope,before_value,after_value,source_channel,correlation_id,reason,occurred_at,
                    previous_hash,record_hash)
                VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?)
                """, UUID.randomUUID(), sequence, actor.actorId(), action, resourceType, resourceId, siteScope,
                jsonOrNull(beforeValue), jsonOrNull(afterValue), sourceChannel, actor.correlationId(), reason,
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC), previousHash, recordHash);
    }

    @Override
    public AuditVerification verifyChain() {
        var rows = jdbc.query("""
                SELECT sequence_no,actor,action,resource_type,resource_id,site_scope,source_channel,occurred_at,
                    previous_hash,record_hash FROM safety_security.audit_log ORDER BY sequence_no
                """, (rs, n) -> new Row(rs.getLong("sequence_no"), rs.getString("actor"), rs.getString("action"),
                rs.getString("resource_type"), rs.getString("resource_id"), rs.getString("site_scope"),
                rs.getString("source_channel"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("previous_hash"), rs.getString("record_hash")));
        String previousHash = null;
        long checked = 0;
        for (Row row : rows) {
            String expected = hash(row.sequenceNo, row.actor, row.action, row.resourceType, row.resourceId,
                    row.siteScope, row.sourceChannel, row.occurredAt, previousHash);
            if (!expected.equals(row.recordHash)) {
                return new AuditVerification(false, checked, row.sequenceNo,
                        "Audit hash divergence at sequence " + row.sequenceNo);
            }
            previousHash = row.recordHash;
            checked++;
        }
        return new AuditVerification(true, checked, null, null);
    }

    private long nextSequence() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0) FROM safety_security.audit_log",
                Long.class);
        return (max == null ? 0L : max) + 1;
    }

    private String hash(long sequence, String actor, String action, String resourceType, String resourceId,
            String siteScope, String sourceChannel, Instant occurredAt, String previousHash) {
        String canonical = sequence + "|" + n(actor) + "|" + n(action) + "|" + n(resourceType) + "|" + n(resourceId)
                + "|" + n(siteScope) + "|" + n(sourceChannel) + "|" + occurredAt.toEpochMilli() + "|" + n(previousHash);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String jsonOrNull(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }

    private record Row(long sequenceNo, String actor, String action, String resourceType, String resourceId,
            String siteScope, String sourceChannel, Instant occurredAt, String previousHash, String recordHash) {
    }
}
