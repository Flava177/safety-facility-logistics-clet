package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceFileStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Evidence bytes in {@code fleet_logistics.fleet_evidence_files}.
 *
 * <p>JDBC rather than JPA, and deliberately so: the rest of the evidence aggregate is mapped with
 * Hibernate, but a BYTEA column on a managed entity is a byte array that gets loaded, dirty-checked
 * and held in the persistence context on every read of the row. The whole point of the separate table
 * is that content is fetched only when somebody asks for it, and a plain query is the honest way to
 * express that.
 *
 * <p>{@code Instant} is bound as a UTC {@link OffsetDateTime} because the PostgreSQL driver cannot
 * infer a SQL type for {@code Instant} - the same reason, and the same fix, as the fuel adapter.
 */
@Repository
public class JdbcEvidenceFileStore implements EvidenceFileStore {

    private final JdbcTemplate jdbc;

    public JdbcEvidenceFileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void store(UUID evidenceId, byte[] content, String contentType, String scanStatus, String scanDetail,
            Instant scannedAt, String uploadedBy) {
        jdbc.update("""
                INSERT INTO fleet_logistics.fleet_evidence_files
                    (evidence_id, content, byte_size, content_type, scan_status, scan_detail, scanned_at,
                     uploaded_by, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                evidenceId, content, (long) content.length, contentType, scanStatus, scanDetail,
                OffsetDateTime.ofInstant(scannedAt, ZoneOffset.UTC), uploadedBy,
                OffsetDateTime.ofInstant(scannedAt, ZoneOffset.UTC));
    }

    @Override
    public Optional<StoredFile> find(UUID evidenceId) {
        return jdbc.query("""
                SELECT evidence_id, content, content_type, byte_size
                FROM fleet_logistics.fleet_evidence_files
                WHERE evidence_id = ?
                """,
                (rs, row) -> new StoredFile(rs.getObject("evidence_id", UUID.class), rs.getBytes("content"),
                        rs.getString("content_type"), rs.getLong("byte_size")),
                evidenceId).stream().findFirst();
    }

    @Override
    public boolean exists(UUID evidenceId) {
        // EXISTS rather than a row-returning query: it always produces exactly one row, so there is
        // no empty-result case to handle, and the planner stops at the first match.
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM fleet_logistics.fleet_evidence_files WHERE evidence_id = ?)",
                Boolean.class, evidenceId));
    }

    @Override
    public Set<UUID> withContent(Collection<UUID> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return Set.of();
        }
        // A generated IN list rather than ANY(?): the array form needs a driver-created java.sql.Array
        // and a connection callback to build it, which is a lot of machinery for a page of at most a
        // few hundred ids. The placeholders are generated from the list's size and every value is
        // still bound, so the count is the only thing that varies between statements.
        List<UUID> ids = List.copyOf(evidenceIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return Set.copyOf(jdbc.query(
                "SELECT evidence_id FROM fleet_logistics.fleet_evidence_files WHERE evidence_id IN ("
                        + placeholders + ")",
                (rs, row) -> rs.getObject("evidence_id", UUID.class),
                ids.toArray()));
    }
}
