package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A project manager's statement that an S164 permit covers one of this project's work types -
 * the "permits linked where required" step of the SRS-SFL-S176-01 workflow.
 *
 * <p>A claim, not a fact. It counts toward the start gate only when the {@link PermitRecord} S164's
 * events have built for the same permit is current, of the same work type and at the same site. A
 * link to a permit S164 has never mentioned is recorded and shown as unconfirmed, and does not let
 * the project start.
 */
public record ProjectPermitLink(
        UUID id,
        UUID projectId,
        String siteCode,
        String permitId,
        String workType,
        RecordMetadata metadata) {

    public ProjectPermitLink {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(permitId, "permitId");
        permitId = permitId.strip();
        workType = EstateCodes.normalize(workType);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ProjectPermitLink link(ConstructionProject project, String permitId, String workType,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ProjectPermitLink(UUID.randomUUID(), project.id(), project.siteCode(), permitId, workType,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Whether the projection shows this link's permit as authorising the work now. */
    public boolean isSatisfiedBy(PermitRecord permit, Instant at) {
        return permit != null && permit.permitId().equals(permitId) && permit.siteCode().equals(siteCode)
                && workType.equals(permit.workType()) && permit.isCurrentAt(at);
    }
}
