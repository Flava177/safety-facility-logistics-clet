package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;

/** Commands for SRS-SFL-S166-03 evidence and audit trail use cases. */
public final class EvidenceCommands {

    private EvidenceCommands() {
    }

    public record RegisterEvidence(
            String siteCode,
            String relatedRecordType,
            String relatedRecordId,
            String evidenceType,
            String fileName,
            String contentType,
            String storageReference,
            String sha256Hash,
            EvidenceRetentionClass retentionClass,
            Instant retentionExpiresAt,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    public record RequestEvidenceExport(
            UUID evidenceId,
            String reason,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    public record DecideEvidenceExport(
            UUID exportRequestId,
            boolean approved,
            String decisionReason,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    public record ExportEvidence(
            UUID exportRequestId,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }
}
