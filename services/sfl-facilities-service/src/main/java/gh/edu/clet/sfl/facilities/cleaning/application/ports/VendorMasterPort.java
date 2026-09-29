package gh.edu.clet.sfl.facilities.cleaning.application.ports;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.List;
import java.util.Optional;

/**
 * Vendor Master (S133) as S169 needs it - SRS-SFL-S169-03 "vendor base is drawn from Vendor Master".
 *
 * <p>S133 is an external system not built in this programme and has no interface S169 can call. The
 * adapter behind this port is therefore a <strong>recorded stand-in</strong>: it resolves only the
 * references an authorised user has recorded as known to S133, and every answer says so in
 * {@link #integrationStatus()}. When S133 exists, the adapter changes and this port does not - the
 * reason the registration workflow depends on this interface rather than on the local table.
 *
 * <p>{@link #recordKnownReference} exists only because of the stand-in. A real S133 integration would
 * drop it; it is on the port rather than hidden in the adapter so that the act of vouching for a vendor
 * is an audited application-layer command with a named person behind it, not a database insert.
 */
public interface VendorMasterPort {

    /** What S133 says about one vendor. */
    record VendorMasterRecord(String reference, String legalName, boolean active) {
    }

    /** The vendor S133 holds under {@code reference} at this site, or empty - CLEANING_VENDOR_NOT_FOUND. */
    Optional<VendorMasterRecord> resolve(String siteCode, String reference);

    /** Records a reference as known to S133, pending a real integration. Idempotent on (site, reference). */
    VendorMasterRecord recordKnownReference(String siteCode, String reference, String legalName, String evidenceNote,
            String actorId, SourceChannel channel, String correlationId);

    List<VendorMasterRecord> knownReferences(String siteCode);

    /** Plain words for health views and responses: whether S133 is integrated, and what stands in for it. */
    String integrationStatus();
}
