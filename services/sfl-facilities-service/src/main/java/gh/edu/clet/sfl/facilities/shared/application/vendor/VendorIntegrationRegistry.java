package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * What IFIMP may truthfully say about each vendor integration - SRS 2026/002 CORR-07, §5.2.
 *
 * <p>"No vendor system may be reported 'integrated' without the Section 5.2-equivalent procurement-gate
 * evidence on file." Phase 1's Go-Live review (gap G-03) found every vendor-facing adapter was a
 * simulator while status documents implied otherwise. This is the one place the answer is computed, so
 * a dashboard, a runbook check or a status report reads it rather than asserting it.
 *
 * <p>There is deliberately no {@code INTEGRATED} value. The strongest thing the service can know is that
 * evidence is on file; whether the evidence is sufficient is a procurement decision recorded in that
 * evidence, not a runtime fact.
 */
@Service
public class VendorIntegrationRegistry {

    private final VendorIntegrationCatalogPort catalog;
    private final FacilitiesAuthorization authorization;

    public VendorIntegrationRegistry(VendorIntegrationCatalogPort catalog, FacilitiesAuthorization authorization) {
        this.catalog = catalog;
        this.authorization = authorization;
    }

    public List<IntegrationStatus> statuses(ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_VENDOR_INTEGRATION_READ, channel,
                "VendorIntegration", "list", "*");
        return catalog.integrations().stream().map(VendorIntegrationRegistry::statusOf).toList();
    }

    /** For a module's own health view: the status of one integration by key. */
    public GateStatus gateStatus(String key) {
        return catalog.integrations().stream()
                .filter(integration -> integration.key().equalsIgnoreCase(key))
                .findFirst()
                .map(VendorIntegrationRegistry::statusOf)
                .map(IntegrationStatus::gateStatus)
                .orElse(GateStatus.NOT_CONFIGURED);
    }

    static IntegrationStatus statusOf(VendorIntegrationCatalogPort.VendorIntegration integration) {
        boolean evidence = integration.procurementGateEvidence() != null
                && !integration.procurementGateEvidence().isBlank();
        GateStatus status = evidence ? GateStatus.GATE_EVIDENCE_ON_FILE : GateStatus.SIMULATED_ADAPTER_ONLY;
        return new IntegrationStatus(integration.key(), integration.system(), integration.name(),
                integration.classification(), integration.adapter(), status,
                evidence ? integration.procurementGateEvidence() : null,
                evidence ? "Procurement-gate evidence is on file: " + integration.procurementGateEvidence() + "."
                        : "Recorded/simulated adapter only. No SRS §5.2 procurement-gate evidence is on file, so "
                                + "this integration must not be reported as integrated.");
    }

    public enum GateStatus {
        SIMULATED_ADAPTER_ONLY,
        GATE_EVIDENCE_ON_FILE,
        NOT_CONFIGURED
    }

    public record IntegrationStatus(String key, String system, String name, String classification, String adapter,
            GateStatus gateStatus, String evidenceReference, String statement) {
    }
}
