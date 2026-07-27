package gh.edu.clet.sfl.emergencynotification.infrastructure.persistence;

import gh.edu.clet.sfl.emergencynotification.application.port.EvidencePort;
import gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Registers governed evidence references (hash, uploader, retention class, related activation, legal hold). */
@Component
public class JdbcEvidenceAdapter implements EvidencePort {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcEvidenceAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID register(EvidenceRegistration r) {
        if (r.retentionClass() == null) {
            throw new IllegalArgumentException("retention class is required");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO emergency_notification.evidence_references (id,site_code,related_activation_id,evidence_type,
                    file_name,content_type,storage_reference,sha256_hash,retention_class,legal_hold,uploaded_by,
                    uploaded_at,source_channel,correlation_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, r.siteCode(), r.activationId(), r.evidenceType(),
                blankTo(r.fileName(), r.evidenceType() + ".dat"), blankTo(r.contentType(), "application/octet-stream"),
                r.storageReference(), blankTo(r.sha256Hash(), "0".repeat(64)), r.retentionClass().name(),
                r.retentionClass() == RetentionClass.LEGAL_HOLD, r.actor().actorId(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), r.sourceChannel().name(),
                r.actor().correlationId());
        return id;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
