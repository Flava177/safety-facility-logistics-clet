package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The standard checklist for a space type at a site - SRS-SFL-S169-02 "a checklist appropriate to the
 * space type".
 *
 * <p>Every item is required: the SRS says completion "requires each checklist item to be marked done".
 * What is configurable is the subset needing a photograph ({@link Item#photoRequired()}), because
 * photographing every bin in every office would make the evidence a chore nobody does properly.
 *
 * <p>Versioned. A new template for the same site and space type supersedes the active one, and a task
 * copies the items at the moment it is raised - so a template tightened on Wednesday does not change
 * what Tuesday's cleaner is held to.
 */
public record ChecklistTemplate(
        UUID id,
        String siteCode,
        SpaceType spaceType,
        String name,
        int version,
        boolean active,
        List<Item> items,
        RecordMetadata metadata) {

    /** One line of the checklist. {@code itemCode} is stable across versions so trends can be compared. */
    public record Item(UUID id, String itemCode, String label, int sequence, boolean photoRequired) {

        public Item {
            Objects.requireNonNull(id, "id is required");
            if (itemCode == null || itemCode.isBlank()) {
                throw new FacilitiesException.ValidationFailedException("Every checklist item needs a code.");
            }
            itemCode = itemCode.strip().toUpperCase(Locale.ROOT);
            if (itemCode.length() > 60) {
                throw new FacilitiesException.ValidationFailedException(
                        "Checklist item codes may be at most 60 characters.");
            }
            if (label == null || label.isBlank()) {
                throw new FacilitiesException.ValidationFailedException(
                        "Checklist item " + itemCode + " needs a label.");
            }
            label = label.strip();
        }
    }

    public ChecklistTemplate {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        if (spaceType == null) {
            throw new FacilitiesException.ValidationFailedException("A checklist template needs a space type.");
        }
        if (name == null || name.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A checklist template needs a name.");
        }
        name = name.strip();
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1");
        }
        if (items == null || items.isEmpty()) {
            // An empty checklist would let a task complete with nothing checked, which is the
            // "claimed, not verified" outcome S169-02 exists to prevent.
            throw new FacilitiesException.ValidationFailedException("A checklist template needs at least one item.");
        }
        Set<String> codes = new HashSet<>();
        for (Item item : items) {
            if (!codes.add(item.itemCode())) {
                throw new FacilitiesException.ValidationFailedException(
                        "Checklist item code " + item.itemCode() + " appears twice.");
            }
        }
        items = List.copyOf(items);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ChecklistTemplate create(UUID id, String siteCode, SpaceType spaceType, String name, int version,
            List<Item> items, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ChecklistTemplate(id, siteCode, spaceType, name, version, true, items,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Superseded by a newer version. Kept, because tasks raised under it still point at it. */
    public ChecklistTemplate supersede(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ChecklistTemplate(id, siteCode, spaceType, name, version, false, items,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
