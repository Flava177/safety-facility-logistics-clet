package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Registers a compliance document against a vehicle (SRS-SFL-S166-01).
 *
 * <p>Registering a document of a type that already has current cover supersedes the earlier one, so
 * the "one current document per type" rule holds without the caller having to withdraw it first.
 */
public record RegisterComplianceDocumentCommand(
        UUID vehicleId,
        ComplianceDocumentType documentType,
        String documentReference,
        String issuingAuthority,
        LocalDate issuedOn,
        LocalDate expiresOn,
        UUID evidenceId,
        EvidenceRetentionClass retentionClass,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "vehicleId", String.valueOf(vehicleId),
                "documentType", String.valueOf(documentType),
                "documentReference", String.valueOf(documentReference),
                "issuedOn", String.valueOf(issuedOn),
                "expiresOn", String.valueOf(expiresOn));
    }
}
