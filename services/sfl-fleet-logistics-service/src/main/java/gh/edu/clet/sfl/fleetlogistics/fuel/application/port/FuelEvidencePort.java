package gh.edu.clet.sfl.fleetlogistics.fuel.application.port;

import java.util.List;
import java.util.UUID;

/**
 * What S168 needs to know about evidence, and nothing else.
 *
 * <p>The fuel module does not register evidence - the dashboard uploads a receipt through the S166
 * evidence endpoint and submits the id it gets back - so this port is read-only, and deliberately
 * narrow. Fuel has exactly one question to ask about a file, and it is a fraud question rather than
 * a document-management one: <em>has this image been used before?</em>
 *
 * <p>Shaped as a port rather than a direct call into the evidence service for the usual reason: the
 * modules are separate bounded contexts that happen to share a deployable, and a fuel rule reaching
 * into {@code FleetEvidenceApplicationService} would make that accidental adjacency load-bearing.
 */
public interface FuelEvidencePort {

    /** One evidence record, reduced to what a fuel rule can act on. */
    record EvidenceFacts(
            UUID id, String siteCode, String sha256Hash, String fileName, boolean hasContent) {}

    java.util.Optional<EvidenceFacts> find(UUID evidenceId);

    /**
     * Other evidence at the same site holding byte-for-byte identical content.
     *
     * <p>The answer to "is this the same photograph they sent last week". It excludes the record
     * asked about, so a non-empty list always means a genuine duplicate.
     *
     * <p>What it cannot see is worth stating: a second photograph of the same receipt is a
     * different file with a different digest, and this will not find it. The digest check is the
     * cheap first pass, not the whole control - which is why it sits beside the posted-price and
     * volume rules rather than in place of them.
     */
    List<EvidenceFacts> findDuplicates(UUID evidenceId);
}
