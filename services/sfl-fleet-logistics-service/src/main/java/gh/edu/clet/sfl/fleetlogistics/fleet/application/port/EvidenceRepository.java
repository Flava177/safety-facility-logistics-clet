package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evidence metadata persistence port (SRS-SFL-S166-03). */
public interface EvidenceRepository {

    EvidenceReference save(EvidenceReference evidence);

    /**
     * Saves and makes the row visible to other statements in the same transaction.
     *
     * <p>Needed by exactly one caller, and the reason is worth stating because it is invisible in the
     * code and fatal at runtime. Evidence metadata is mapped with JPA; evidence <em>content</em> is
     * written with plain JDBC, and the content table has a foreign key onto the metadata table. JPA
     * defers its INSERT to the end of the transaction, so the JDBC write reaches the database first
     * and the foreign key fails - a defect no unit test with an in-memory repository can produce,
     * because there is no persistence context to defer anything.
     *
     * <p>{@link #save} is deliberately left alone. Every other caller writes one row and never reads
     * it back through a different mechanism in the same transaction, and flushing them all would give
     * up the batching that write-behind exists for.
     */
    EvidenceReference saveAndFlush(EvidenceReference evidence);

    Optional<EvidenceReference> findById(UUID id);

    List<EvidenceReference> findByRelatedRecord(String relatedRecordType, String relatedRecordId,
            SiteScopeFilter scope);

    /**
     * Everything at a site already filed with these exact bytes.
     *
     * <p>Exists for one question, and it is worth stating plainly because the answer is a fraud
     * control rather than a lookup: has this photograph been submitted before? A driver who
     * photographs a pump once and attaches the same image to four claims is the cheapest fraud there
     * is to commit and, with this, the cheapest to catch - the digest is already computed and already
     * indexed, so the check costs one query.
     *
     * <p>Scoped by site because a digest collision across sites is not evidence of anything, and
     * because two vehicles legitimately filling at one pump in one minute is not the same photograph.
     *
     * <p>The caller passes the id being registered so the record can exclude itself; a new registration
     * passes null.
     */
    List<EvidenceReference> findBySha256(String siteCode, String sha256Hash, UUID excludingId);
}
