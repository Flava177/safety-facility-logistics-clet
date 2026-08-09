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

    /**
     * Register evidence from the file itself.
     *
     * <p>Note what this command does <em>not</em> carry, next to {@link RegisterEvidence}: no content
     * type, no storage reference and no hash. All three are derived from the bytes by the scanner. A
     * caller cannot assert what it is uploading, only upload it - which is the difference between a
     * digest that attests to something and a digest that was copied from a form field.
     */
    public record UploadEvidence(
            String siteCode,
            String relatedRecordType,
            String relatedRecordId,
            String evidenceType,
            String fileName,
            /** What the client said the type was. Cross-checked against the bytes, never trusted. */
            String declaredContentType,
            byte[] content,
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
