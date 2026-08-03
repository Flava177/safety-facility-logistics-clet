package gh.edu.clet.sfl.fleetlogistics.fleet.api.request;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** HTTP request DTOs for SRS-SFL-S166-03 evidence governance. */
public final class FleetEvidenceRequests {

    private FleetEvidenceRequests() {
    }

    public record RegisterEvidence(
            @NotBlank String siteCode,
            @NotBlank String relatedRecordType,
            @NotBlank String relatedRecordId,
            @NotBlank String evidenceType,
            @NotBlank String fileName,
            @NotBlank String contentType,
            @NotBlank String storageReference,
            @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$",
                    message = "sha256Hash must be a 64-character SHA-256 hex digest") String sha256Hash,
            @NotNull EvidenceRetentionClass retentionClass,
            Instant retentionExpiresAt) {
    }

    public record RequestExport(@NotBlank String reason) {
    }

    public record DecideExport(boolean approved, @NotBlank String decisionReason) {
    }
}
