package gh.edu.clet.sfl.facilities.shared.application.vendor;

import java.util.List;

/** The Phase 2 vendor integrations this service carries, and the procurement evidence on file for each. */
public interface VendorIntegrationCatalogPort {

    List<VendorIntegration> integrations();

    /**
     * @param procurementGateEvidence a reference to the SRS §5.2 evidence pack - a document id, not a
     *        yes/no flag. Blank means none is on file, which is the state of every integration today.
     */
    record VendorIntegration(String key, String system, String name, String classification, String adapter,
            String procurementGateEvidence) {
    }
}
