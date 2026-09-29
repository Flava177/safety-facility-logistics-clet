package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The accountable approver's sign-off - SRS-SFL-S176-01 "no project moves from approved to in progress
 * without a recorded sign-off from the accountable approver".
 *
 * <p>A record of its own rather than a flag on the project, because the sign-off is a fact about a
 * person and a number: who signed, and which baseline they signed. {@code baselineRevision} is what
 * lets the start gate tell a sign-off that still covers the project from one that covered a baseline
 * since revised.
 *
 * <p>{@code baselineAmount} is also the "original budget" S176-03's escalation threshold is measured
 * against - the budget somebody accountable agreed to, not whatever the baseline was first typed as.
 */
public record ProjectApproval(
        UUID id,
        UUID projectId,
        String siteCode,
        String approverId,
        int baselineRevision,
        BigDecimal baselineAmount,
        String currency,
        String note,
        Instant approvedAt,
        RecordMetadata metadata) {

    public ProjectApproval {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(approverId, "approverId");
        Objects.requireNonNull(baselineAmount, "baselineAmount is required");
        EstateCodes.require(currency, "currency");
        note = EstateCodes.blankToNull(note);
        Objects.requireNonNull(approvedAt, "approvedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** Whether this sign-off covers the project as it now stands. */
    public boolean covers(ConstructionProject project) {
        return projectId.equals(project.id()) && baselineRevision == project.baselineRevision();
    }
}
