package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A contractor made responsible for part of a project - SRS-SFL-S176-01 "the responsible
 * contractor(s)".
 *
 * <p>Also what a defect is tagged against (S176-04): a defects-liability item names the contractor
 * who carries the liability, and only a contractor assigned to the project can.
 */
public record ProjectContractor(
        UUID id,
        UUID projectId,
        UUID contractorId,
        String siteCode,
        Role role,
        RecordMetadata metadata) {

    /** What the contractor is on this project. */
    public enum Role {
        MAIN_CONTRACTOR,
        SUBCONTRACTOR,
        CONSULTANT
    }

    public ProjectContractor {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(contractorId, "contractorId is required");
        siteCode = EstateCodes.normalize(siteCode);
        role = role == null ? Role.MAIN_CONTRACTOR : role;
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ProjectContractor assign(ConstructionProject project, Contractor contractor, Role role,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ProjectContractor(UUID.randomUUID(), project.id(), contractor.id(), project.siteCode(), role,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
