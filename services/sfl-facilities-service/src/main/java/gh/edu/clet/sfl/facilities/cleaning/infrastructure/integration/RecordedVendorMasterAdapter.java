package gh.edu.clet.sfl.facilities.cleaning.infrastructure.integration;

import gh.edu.clet.sfl.facilities.cleaning.application.ports.VendorMasterPort;
import gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence.CleaningVendorMasterReferenceRecord;
import gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence.JpaCleaningVendorMasterReferenceRepository;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The recorded stand-in for Vendor Master (S133) - <strong>not an integration</strong>.
 *
 * <p>S133 is an external CLET system that is not built in this programme and exposes nothing S169 can
 * call. This adapter resolves a vendor reference only if an authorised user has recorded it in
 * {@code facilities.cleaning_vendor_master_references} as known to S133, and every answer carries
 * {@link #STATUS} so no screen, runbook or report can present the result as S133 having confirmed it.
 * Replacing this class with a real S133 client changes nothing else in the module: the registration
 * workflow depends on {@link VendorMasterPort}, and {@code CLEANING_VENDOR_NOT_FOUND} is raised by the
 * application service from an empty {@link #resolve} whichever adapter is behind it.
 */
@Component
public class RecordedVendorMasterAdapter implements VendorMasterPort {

    static final String STATUS = "Vendor Master (S133) is NOT integrated: vendors resolve only against references "
            + "recorded locally by an authorised user as known to S133.";

    private final JpaCleaningVendorMasterReferenceRepository references;
    private final Clock clock;

    public RecordedVendorMasterAdapter(JpaCleaningVendorMasterReferenceRepository references, Clock clock) {
        this.references = references;
        this.clock = clock;
    }

    @Override
    public Optional<VendorMasterRecord> resolve(String siteCode, String reference) {
        if (siteCode == null || reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        return references.findByReference(normalize(siteCode), normalize(reference)).map(RecordedVendorMasterAdapter::of);
    }

    @Override
    public VendorMasterRecord recordKnownReference(String siteCode, String reference, String legalName,
            String evidenceNote, String actorId, SourceChannel channel, String correlationId) {
        String site = normalize(siteCode);
        String ref = normalize(reference);
        Optional<CleaningVendorMasterReferenceRecord> existing = references.findByReference(site, ref);
        if (existing.isPresent()) {
            return of(existing.get());
        }
        return of(references.saveAndFlush(CleaningVendorMasterReferenceRecord.create(site, ref, legalName.strip(),
                evidenceNote == null || evidenceNote.isBlank() ? null : evidenceNote.strip(), actorId, clock.instant(),
                channel, correlationId)));
    }

    @Override
    public List<VendorMasterRecord> knownReferences(String siteCode) {
        return references.findForSite(normalize(siteCode)).stream().map(RecordedVendorMasterAdapter::of).toList();
    }

    @Override
    public String integrationStatus() {
        return STATUS;
    }

    private static VendorMasterRecord of(CleaningVendorMasterReferenceRecord record) {
        return new VendorMasterRecord(record.reference(), record.legalName(), record.active());
    }

    private static String normalize(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
