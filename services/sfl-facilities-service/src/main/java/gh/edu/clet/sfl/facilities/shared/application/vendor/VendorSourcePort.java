package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.util.Optional;
import java.util.Set;

/**
 * The allowlist: which vendor sources may post, on which channel, for which sites, with which secret.
 *
 * <p>A port so the secret never has to be named by application code. The shipped adapter reads it from
 * configuration bound to environment variables; a production deployment points it at the platform
 * secret store instead (SRS §5.2: "no requirement for SFL to hold vendor-specific credentials outside
 * the platform's secure secret store") by replacing the adapter, not the verifier.
 */
public interface VendorSourcePort {

    Optional<VendorSource> find(String sourceId);

    /** How far a message's signing time may be from now. Five minutes, the platform's established default. */
    default java.time.Duration signatureWindow() {
        return java.time.Duration.ofMinutes(5);
    }

    /**
     * @param sites the sites this source may report for. {@code *} means any - acceptable for a
     *        simulator, and a finding in a production configuration: one site's BMS gateway has no
     *        business reporting another's.
     */
    record VendorSource(String sourceId, VendorChannel channel, String secret, Set<String> sites) {

        public boolean mayReportFor(String siteCode) {
            return sites.contains("*") || (siteCode != null && sites.contains(siteCode));
        }
    }
}
