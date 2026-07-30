package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A CLET site — the root of the estate hierarchy and the unit of site-scoped authorisation
 * (SRS-SFL-S152-01).
 *
 * <p>The site is also where {@link OperatingMode} lives. An examination is declared over a centre,
 * not over a room, and every space beneath inherits the stricter rules — so mode belongs here and
 * nowhere else (NFR 23.3).
 *
 * <p>This aggregate carries the shared code-normalisation helpers the rest of the estate calls. That
 * coupling is deliberate: one normalisation rule applied everywhere is what makes {@code "main"} and
 * {@code " MAIN "} the same site to every module in the platform.
 */
public record Site(
        UUID id,
        String siteCode,
        String name,
        String description,
        RecordLifecycleStatus lifecycleStatus,
        OperatingMode operatingMode,
        Instant operatingModeChangedAt,
        String operatingModeChangedBy,
        RecordMetadata metadata) {

    public Site {
        Objects.requireNonNull(id, "id is required");
        requireText(siteCode, "siteCode");
        requireText(name, "name");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(operatingMode, "operatingMode is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** Registers a new site in {@link RecordLifecycleStatus#ACTIVE} and {@link OperatingMode#ROUTINE}. */
    public static Site create(UUID id, String siteCode, String name, String description, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new Site(id, normalizeCode(siteCode), name.strip(), blankToNull(description),
                RecordLifecycleStatus.ACTIVE, OperatingMode.ROUTINE, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A null or blank field leaves the current value alone — this is a PATCH, not a replace. */
    public Site update(String name, String description, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Site(id, siteCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                description == null ? this.description : blankToNull(description),
                lifecycleStatus, operatingMode, operatingModeChangedAt, operatingModeChangedBy,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public Site changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Site(id, siteCode, name, description, lifecycleStatus.transitionTo(target, "Site"),
                operatingMode, operatingModeChangedAt, operatingModeChangedBy,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Declares or stands down examination mode.
     *
     * <p>Refuses a no-op rather than accepting it silently. NFR 23.3 requires mode changes to be
     * "explicit, audited and reversible only by authorised roles" — and an audit trail containing a
     * change from EXAMINATION to EXAMINATION records a decision nobody made.
     */
    public Site changeOperatingMode(OperatingMode target, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(target, "operatingMode is required");
        if (target == operatingMode) {
            throw new FacilitiesException.OperatingModeTransitionException(
                    "Site " + siteCode + " is already in " + target + " mode.");
        }
        if (!lifecycleStatus.isOperational()) {
            throw new FacilitiesException.OperatingModeTransitionException(
                    "Site " + siteCode + " is " + lifecycleStatus + " and cannot change operating mode.");
        }
        return new Site(id, siteCode, name, description, lifecycleStatus, target, at, actorId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when the site is in service. Preserves the field the pre-S152 API exposed. */
    public boolean active() {
        return lifecycleStatus.isOperational();
    }

    /** {@code true} when an examination is in progress or being set up at this site. */
    public boolean inExaminationMode() {
        return operatingMode == OperatingMode.EXAMINATION;
    }

    // The estate aggregates call these through Site for historical reasons; the rule itself lives in
    // EstateCodes so the readiness module can apply the same one without depending on this package.

    static String normalizeCode(String value) {
        return EstateCodes.normalize(value);
    }

    static String blankToNull(String value) {
        return EstateCodes.blankToNull(value);
    }

    static void requireText(String value, String field) {
        EstateCodes.require(value, field);
    }
}
