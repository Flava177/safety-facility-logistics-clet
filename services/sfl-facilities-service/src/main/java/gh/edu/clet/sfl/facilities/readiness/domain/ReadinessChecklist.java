package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A runtime-configurable set of readiness questions (NFR 23.8: "readiness checklists ... must be
 * runtime-configurable and versioned").
 *
 * <p>Applicability is by {@code spaceType} and {@code operatingMode}, both nullable and both meaning
 * "any" when null. That pair is what lets one site run a short routine checklist over every room and
 * a long examination checklist over its halls, without either list containing questions that make no
 * sense for the space in front of the assessor.
 *
 * <p>{@code version} increments on every change. An assessment records the version it was taken
 * against, so a result from March can still be read against the questions that were asked in March —
 * which is the entire reason NFR 23.8 says "versioned" rather than "configurable".
 */
public record ReadinessChecklist(
        UUID id,
        String siteCode,
        String checklistCode,
        String name,
        String description,
        SpaceType spaceType,
        OperatingMode operatingMode,
        int version,
        List<ReadinessChecklistItem> items,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public ReadinessChecklist {
        Objects.requireNonNull(id, "id is required");
        EstateCodes.require(siteCode, "siteCode");
        EstateCodes.require(checklistCode, "checklistCode");
        EstateCodes.require(name, "name");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static ReadinessChecklist create(UUID id, String siteCode, String checklistCode, String name,
            String description, SpaceType spaceType, OperatingMode operatingMode, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ReadinessChecklist(id, EstateCodes.normalize(siteCode), EstateCodes.normalize(checklistCode),
                name.strip(), EstateCodes.blankToNull(description), spaceType, operatingMode, 1, List.of(),
                RecordLifecycleStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Replaces the checklist's questions and bumps the version.
     *
     * <p>Replace rather than merge: a checklist is read as a whole by whoever is standing in the room,
     * and a partial update that left an obsolete question in place would have them assessing against a
     * list nobody approved.
     */
    public ReadinessChecklist withItems(List<ReadinessChecklistItem> newItems, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (newItems == null || newItems.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A readiness checklist must contain at least one item.");
        }
        long distinctCodes = newItems.stream().map(ReadinessChecklistItem::itemCode).distinct().count();
        if (distinctCodes != newItems.size()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Readiness checklist item codes must be unique within the checklist.");
        }
        return new ReadinessChecklist(id, siteCode, checklistCode, name, description, spaceType, operatingMode,
                version + 1, newItems, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public ReadinessChecklist update(String name, String description, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ReadinessChecklist(id, siteCode, checklistCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                description == null ? this.description : EstateCodes.blankToNull(description),
                spaceType, operatingMode, version, items, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public ReadinessChecklist changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ReadinessChecklist(id, siteCode, checklistCode, name, description, spaceType, operatingMode,
                version, items, lifecycleStatus.transitionTo(target, "Readiness checklist"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when this checklist should be offered for a space of this type in this mode. */
    public boolean appliesTo(SpaceType type, OperatingMode mode) {
        return lifecycleStatus.isOperational()
                && (spaceType == null || spaceType == type)
                && (operatingMode == null || operatingMode == mode);
    }

    /** The sum of every item's weight — the denominator of the readiness score. */
    public int totalWeight() {
        return items.stream().mapToInt(ReadinessChecklistItem::weight).sum();
    }
}
