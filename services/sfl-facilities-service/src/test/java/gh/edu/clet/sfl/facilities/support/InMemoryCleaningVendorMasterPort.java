package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.cleaning.application.ports.VendorMasterPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link VendorMasterPort} double, reproducing the same honesty the recorded production
 * adapter states: it resolves only references a test has explicitly recorded, and it never claims S133
 * is integrated.
 */
public class InMemoryCleaningVendorMasterPort implements VendorMasterPort {

    static final String STATUS = "Vendor Master (S133) is NOT integrated (test double).";

    private final Map<String, VendorMasterRecord> known = new LinkedHashMap<>();

    @Override
    public Optional<VendorMasterRecord> resolve(String siteCode, String reference) {
        return Optional.ofNullable(known.get(key(siteCode, reference)));
    }

    @Override
    public VendorMasterRecord recordKnownReference(String siteCode, String reference, String legalName,
            String evidenceNote, String actorId, SourceChannel channel, String correlationId) {
        VendorMasterRecord record = new VendorMasterRecord(reference.strip().toUpperCase(Locale.ROOT),
                legalName.strip(), true);
        known.put(key(siteCode, reference), record);
        return record;
    }

    @Override
    public List<VendorMasterRecord> knownReferences(String siteCode) {
        return known.values().stream().toList();
    }

    @Override
    public String integrationStatus() {
        return STATUS;
    }

    private static String key(String siteCode, String reference) {
        return siteCode.strip().toUpperCase(Locale.ROOT) + "|" + reference.strip().toUpperCase(Locale.ROOT);
    }
}
