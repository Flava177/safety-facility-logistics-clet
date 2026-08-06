package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceFileStore;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelEvidencePort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads S166 evidence on behalf of the fuel reconciliation rules.
 *
 * <p>No permission check here, and that is intentional rather than an omission. Reconciliation has
 * already authorised its caller against the transaction's own site through
 * {@code FUEL_RECONCILIATION_RUN}, and the facts this returns - a digest and whether bytes exist -
 * are about the evidence the transaction itself points at. Re-checking a fleet evidence permission
 * would mean a reconciliation run failing because the person running it cannot browse the evidence
 * register, which is a different question and the wrong one to ask here.
 *
 * <p>Nothing on this path returns file contents or a storage reference, so there is no route through
 * it to read a document the caller could not otherwise read.
 */
@Component
public class EvidenceLookupAdapter implements FuelEvidencePort {

    private final EvidenceRepository evidence;
    private final EvidenceFileStore files;

    public EvidenceLookupAdapter(EvidenceRepository evidence, EvidenceFileStore files) {
        this.evidence = evidence;
        this.files = files;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceFacts> find(UUID evidenceId) {
        if (evidenceId == null) {
            return Optional.empty();
        }
        return evidence.findById(evidenceId).map(this::facts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceFacts> findDuplicates(UUID evidenceId) {
        if (evidenceId == null) {
            return List.of();
        }
        return evidence.findById(evidenceId)
                .map(reference -> evidence
                        .findBySha256(reference.siteCode().value(), reference.sha256Hash(), reference.id())
                        .stream()
                        .map(this::facts)
                        .toList())
                .orElseGet(List::of);
    }

    private EvidenceFacts facts(EvidenceReference reference) {
        return new EvidenceFacts(reference.id(), reference.siteCode().value(), reference.sha256Hash(),
                reference.fileName(), files.exists(reference.id()));
    }
}
