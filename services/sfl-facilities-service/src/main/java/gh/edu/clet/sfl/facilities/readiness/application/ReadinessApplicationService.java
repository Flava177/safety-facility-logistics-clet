package gh.edu.clet.sfl.facilities.readiness.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.SpaceReadinessPort;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ExternalBlockerPort;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklistItem;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessOutcome;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Readiness checklists, assessments, blockers and locks (SRS-SFL-S152-01, -02, -05).
 *
 * <p>The one rule everything here serves: <strong>a space cannot be READY while a critical blocker is
 * open.</strong> It is enforced in exactly two places - {@link #submitAssessment}, where a derived
 * status is computed, and {@link #setReadinessDirectly}, where a status is set by hand - and both
 * route through {@link ReadinessPolicy}, so there is no third path around it.
 *
 * <p>Implements {@link SpaceReadinessPort} so the asset register can tell readiness that an asset
 * changed without depending on this package. See the port for why the arrow points that way.
 */
@Service
public class ReadinessApplicationService implements SpaceReadinessPort, ExternalBlockerPort {

    private final ReadinessRepository readiness;
    private final FacilitiesRepository facilities;
    private final ServiceOutbox outbox;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final FacilitiesAuthorization authorization;
    private final Clock clock;
    private final ReadinessAssessmentCommands assessmentCommands;
    private final ReadinessBlockerOperations blockerOperations;

    public ReadinessApplicationService(ReadinessRepository readiness, FacilitiesRepository facilities,
            ServiceOutbox outbox, AuditPort audit, IdempotencyPort idempotency,
            FacilitiesAuthorization authorization, Clock clock) {
        this.readiness = readiness;
        this.facilities = facilities;
        this.outbox = outbox;
        this.audit = audit;
        this.idempotency = idempotency;
        this.authorization = authorization;
        this.clock = clock;
        this.assessmentCommands = new ReadinessAssessmentCommands(this, readiness, authorization, audit,
                idempotency);
        this.blockerOperations = new ReadinessBlockerOperations(this, readiness, facilities, authorization,
                audit);
    }

    // =========================================================================================
    // Checklists
    // =========================================================================================

    @Transactional
    public ReadinessChecklist createChecklist(ReadinessCommands.CreateChecklist command) {
        ActorContext actor = command.actor();
        String siteCode = normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE, siteCode,
                command.channel(), "ReadinessChecklist", normalize(command.checklistCode()));

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<ReadinessChecklist> replayed = idempotency
                    .findExistingResult("create-readiness-checklist", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(readiness::findChecklist);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        String checklistCode = normalize(command.checklistCode());
        readiness.findChecklistByCode(siteCode, checklistCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("readiness checklist", checklistCode,
                        siteCode);
            }
        });

        UUID id = UUID.randomUUID();
        ReadinessChecklist checklist = ReadinessChecklist.create(id, siteCode, checklistCode, command.name(),
                command.description(), command.spaceType(), command.operatingMode(), actor.actorId(), now(),
                command.channel(), actor.correlationId());
        // A checklist with no items assesses nothing, so the items are part of creation rather than a
        // second call that a caller could forget to make.
        checklist = checklist.withItems(toItems(id, command.items()), actor.actorId(), now(), command.channel(),
                actor.correlationId());

        ReadinessChecklist saved = readiness.saveChecklist(checklist);
        audit.record(actor, command.channel(), AuditAction.READINESS_CHECKLIST_CREATED, "ReadinessChecklist",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("sfl.ifimp.readiness-checklist-created.v1", "ReadinessChecklist", saved.id(), saved.siteCode(), actor,
                saved);
        idempotency.recordResult("create-readiness-checklist", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    @Transactional
    public ReadinessChecklist updateChecklist(ReadinessCommands.UpdateChecklist command) {
        ActorContext actor = command.actor();
        ReadinessChecklist checklist = requireChecklist(command.checklistId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE, checklist.siteCode(),
                command.channel(), "ReadinessChecklist", checklist.id().toString());
        checklist.metadata().requireVersion(command.expectedVersion(), "Readiness checklist", checklist.id());

        ReadinessChecklist updated = checklist.update(command.name(), command.description(), actor.actorId(),
                now(), command.channel(), actor.correlationId());
        if (command.items() != null && !command.items().isEmpty()) {
            updated = updated.withItems(toItems(checklist.id(), command.items()), actor.actorId(), now(),
                    command.channel(), actor.correlationId());
        }

        ReadinessChecklist saved = readiness.saveChecklist(updated);
        audit.record(actor, command.channel(), AuditAction.READINESS_CHECKLIST_UPDATED, "ReadinessChecklist",
                saved.id().toString(), saved.siteCode(), checklist, saved);
        publish("sfl.ifimp.readiness-checklist-updated.v1", "ReadinessChecklist", saved.id(), saved.siteCode(), actor,
                saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReadinessChecklist> checklists(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, channel, "ReadinessChecklist",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "ReadinessChecklist");
        return authorization.filterBySite(actor, readiness.findChecklists(siteCode),
                ReadinessChecklist::siteCode);
    }

    @Transactional(readOnly = true)
    public ReadinessChecklist checklist(UUID id, ActorContext actor, SourceChannel channel) {
        ReadinessChecklist checklist = requireChecklist(id);
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, checklist.siteCode(), channel,
                "ReadinessChecklist", id.toString());
        return checklist;
    }

    // =========================================================================================
    // Assessments
    // =========================================================================================

    /**
     * Records an inspection and re-derives the space's readiness.
     *
     * <p>The whole sequence happens in one transaction, because a space whose assessment committed and
     * whose blockers did not would report itself ready on a checklist it failed:
     * <ol>
     *   <li>resolve the checklist (explicit, or by space type and operating mode),</li>
     *   <li>snapshot each answer against the item as it is worded today,</li>
     *   <li>resolve the blockers the previous assessment raised - they are being reassessed,</li>
     *   <li>raise a blocker for each failed item, at the item's declared severity,</li>
     *   <li>evaluate every open blocker, including ones from assets and manual raises,</li>
     *   <li>write the derived status back to the space.</li>
     * </ol>
     */
    @Transactional
    public ReadinessAssessment submitAssessment(ReadinessCommands.SubmitAssessment command) {
        return assessmentCommands.submit(command);
    }

    @Transactional(readOnly = true)
    public RepositoryPage<ReadinessAssessment> assessments(String siteCode, UUID roomId, int page, int size,
            ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, channel, "ReadinessAssessment",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "ReadinessAssessment");
        RepositoryPage<ReadinessAssessment> found = readiness.findAssessments(siteCode, roomId, page, size);
        List<ReadinessAssessment> visible = authorization.filterBySite(actor, found.items(),
                ReadinessAssessment::siteCode);
        // When filtering removed rows, the total is reported as what remains: a total counting records
        // the caller may not see would let them infer another site's estate size.
        return visible.size() == found.items().size()
                ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    @Transactional(readOnly = true)
    public ReadinessAssessment assessment(UUID id, ActorContext actor, SourceChannel channel) {
        ReadinessAssessment assessment = readiness.findAssessment(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Readiness assessment", id));
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, assessment.siteCode(), channel,
                "ReadinessAssessment", id.toString());
        return assessment;
    }

    // =========================================================================================
    // Blockers
    // =========================================================================================

    @Transactional
    public ReadinessBlocker raiseBlocker(ReadinessCommands.RaiseBlocker command) {
        return blockerOperations.raise(command);
    }

    /**
     * Closes a blocker and re-derives the space's readiness.
     *
     * <p>Resolving the last open critical blocker is what lets a space become READY again, so the
     * recompute is not an optimisation - it is the second half of the operation.
     */
    @Transactional
    public ReadinessBlocker resolveBlocker(ReadinessCommands.ResolveBlocker command) {
        return blockerOperations.resolve(command);
    }

    @Transactional(readOnly = true)
    public RepositoryPage<ReadinessBlocker> blockers(String siteCode, UUID roomId, BlockerSeverity severity,
            Boolean open, int page, int size, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, channel, "ReadinessBlocker",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "ReadinessBlocker");
        RepositoryPage<ReadinessBlocker> found = readiness.findBlockers(siteCode, roomId, severity, open, page,
                size);
        List<ReadinessBlocker> visible = authorization.filterBySite(actor, found.items(),
                ReadinessBlocker::siteCode);
        // When filtering removed rows, the total is reported as what remains: a total counting records
        // the caller may not see would let them infer another site's estate size.
        return visible.size() == found.items().size()
                ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    // =========================================================================================
    // Manual readiness and locks
    // =========================================================================================

    /**
     * Sets a space's readiness by hand.
     *
     * <p>Kept because an officer standing in a room sometimes knows something the checklist does not.
     * It is still subject to the critical-blocker rule: {@link ReadinessPolicy#requireReadyPermitted}
     * runs first, so this overrides the <em>process</em> and never the <em>invariant</em>.
     */
    @Transactional
    public FacilityRoom setReadinessDirectly(FacilitiesCommands.UpdateRoomReadiness command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_ASSESS, room.siteCode(),
                command.channel(), "FacilityRoom", room.id().toString());

        List<ReadinessBlocker> open = readiness.findOpenBlockers(room.id());
        ReadinessPolicy.requireReadyPermitted(command.status(), open);

        Instant at = now();
        FacilityRoom saved = facilities.saveRoom(room.applyReadiness(command.status(), command.notes(),
                actor.actorId(), at, command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_READINESS_CHANGED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), room.readinessStatus(), saved.readinessStatus());
        publish("sfl.ifimp.room-readiness-changed.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    @Transactional
    public FacilityRoom lockReadiness(ReadinessCommands.LockReadiness command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_OVERRIDE, room.siteCode(),
                command.channel(), "FacilityRoom", room.id().toString());

        Instant at = now();
        FacilityRoom saved = facilities.saveRoom(room.lockReadiness(actor.actorId(), at, command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.READINESS_LOCK_ENGAGED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), null, command.reason());
        publish("sfl.ifimp.readiness-lock-engaged.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    @Transactional
    public FacilityRoom unlockReadiness(ReadinessCommands.UnlockReadiness command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_OVERRIDE, room.siteCode(),
                command.channel(), "FacilityRoom", room.id().toString());

        Instant at = now();
        FacilityRoom saved = facilities.saveRoom(room.unlockReadiness(actor.actorId(), at, command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.READINESS_LOCK_RELEASED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), room.readinessLockedBy(), command.reason());
        publish("sfl.ifimp.readiness-lock-released.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    // =========================================================================================
    // SpaceReadinessPort - called by the asset register
    // =========================================================================================

    /**
     * Reconciles the blockers derived from one asset.
     *
     * <p>Keyed on the asset's id as the blocker's {@code sourceReference}, so an asset that recovers
     * closes exactly the blockers it opened and nothing else. An asset that is impaired but already has
     * an open blocker at the right severity is left alone - re-raising on every save would fill the
     * queue with duplicates of one fault.
     */
    @Override
    @Transactional
    public void reconcileAssetBlockers(FacilityAsset asset, ActorContext actor, SourceChannel channel) {
        blockerOperations.reconcileAssetBlockers(asset, actor, channel);
    }

    // =========================================================================================
    // ExternalBlockerPort - what another module may do to a space's readiness
    // =========================================================================================

    /**
     * {@inheritDoc}
     *
     * <p>The body is the asset reconciliation above with the asset taken out of it: find what this
     * source already has open, leave it alone if it is already at the right severity, close it if the
     * severity has moved, raise one if there is none. Written out rather than shared with
     * {@code reconcileAssetBlockers} because that method also decides *whether* an asset is impaired,
     * which is a judgement only it can make; this one is told the severity and applies it.
     */
    @Override
    @Transactional
    public UUID raiseExternalBlocker(UUID roomId, BlockerSource source, String sourceReference,
            BlockerSeverity severity, String description, ActorContext actor, SourceChannel channel) {
        return blockerOperations.raiseExternal(roomId, source, sourceReference, severity, description, actor,
                channel);
    }

    @Override
    @Transactional
    public int resolveExternalBlockers(BlockerSource source, String sourceReference, String resolutionNotes,
            ActorContext actor, SourceChannel channel) {
        return blockerOperations.resolveExternal(source, sourceReference, resolutionNotes, actor, channel);
    }

    // =========================================================================================
    // Internals
    // =========================================================================================

    /** Evaluates a space's readiness from every open blocker and its most recent score. */
    @Transactional(readOnly = true)
    public ReadinessOutcome evaluate(UUID roomId) {
        List<ReadinessBlocker> open = readiness.findOpenBlockers(roomId);
        Optional<ReadinessAssessment> latest = readiness.findLatestAssessment(roomId);
        return ReadinessPolicy.evaluate(open, latest.map(ReadinessAssessment::score).orElse(0),
                latest.isPresent());
    }

    /**
     * One space's current readiness, checked against <em>that space's</em> site.
     *
     * <h2>The hole this closes</h2>
     *
     * <p>{@code GET /readiness/rooms/{roomId}} authorised itself by calling {@link #blockers} with a
     * null site code and discarding the result, on the reasoning that the blockers are the data being
     * returned. They are not: the response comes from {@link #evaluate}, which takes no actor and
     * filters nothing.
     *
     * <p>A null site code takes {@code requireRequestedSite} down to {@code requireAnySiteScope} -
     * "you hold at least one site, somewhere" - so the room's own site was never compared to the
     * caller's scopes. An actor scoped only to Kumasi, holding nothing but
     * {@code FACILITIES_READINESS_READ}, could read an Accra room's readiness status, score and
     * blocker summary by pasting a room id. That is the by-id scope skip A0 found in fuel, in a
     * different module.
     *
     * <p>Resolving the room first and checking {@code room.siteCode()} is what every sibling read on
     * this controller already does. A missing room is a 404 before any of it, so this does not become
     * a way to enumerate room ids.
     */
    @Transactional(readOnly = true)
    public ReadinessOutcome roomReadiness(UUID roomId, ActorContext actor, SourceChannel channel) {
        FacilityRoom room = facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("FacilityRoom", roomId));
        authorization.require(actor, SflPermission.FACILITIES_READINESS_READ, room.siteCode(), channel,
                "FacilityRoom", roomId.toString());
        return evaluate(roomId);
    }

    /** Writes a derived outcome back onto the space, unless nothing changed. */
    void applyOutcome(FacilityRoom room, ReadinessOutcome outcome, ActorContext actor,
            SourceChannel channel, Instant at) {
        FacilityRoom current = facilities.findRoom(room.id()).orElse(room);
        LocationReadinessStatus previous = current.readinessStatus();
        FacilityRoom saved = facilities.saveRoom(current.applyReadiness(outcome.status(), outcome.summary(),
                actor.actorId(), at, channel, actor.correlationId()));
        if (previous != outcome.status()) {
            audit.record(actor, channel, AuditAction.ROOM_READINESS_CHANGED, "FacilityRoom",
                    saved.id().toString(), saved.siteCode(), previous, outcome.status());
            publish("sfl.ifimp.room-readiness-changed.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        }
    }

    /**
     * The checklist an assessment is taken against.
     *
     * <p>Explicit id wins. Otherwise the applicable one is resolved from the space's type and the
     * site's operating mode, and if there is none the assessment still proceeds with no items - a space
     * with no configured checklist can still carry manual blockers, and refusing the whole operation
     * would make an unconfigured site look broken rather than unconfigured.
     */
    ReadinessChecklist resolveChecklist(UUID checklistId, FacilityRoom room, OperatingMode mode) {
        if (checklistId != null) {
            ReadinessChecklist checklist = requireChecklist(checklistId);
            if (!checklist.siteCode().equals(room.siteCode())) {
                throw new FacilitiesException.ValidationFailedException(
                        "Checklist " + checklist.checklistCode() + " belongs to site " + checklist.siteCode()
                                + ", not " + room.siteCode() + ".");
            }
            return checklist;
        }
        return readiness.findApplicableChecklist(room.siteCode(), room.spaceType(), mode).orElse(null);
    }

    /** Snapshots each answer against the checklist item as it is worded today. */
    List<ReadinessAssessmentItem> answerItems(UUID assessmentId, ReadinessChecklist checklist,
            List<ReadinessCommands.AssessmentAnswer> answers) {
        if (checklist == null) {
            return List.of();
        }
        Map<String, ReadinessCommands.AssessmentAnswer> byCode = new LinkedHashMap<>();
        if (answers != null) {
            answers.forEach(answer -> byCode.put(normalize(answer.itemCode()), answer));
        }

        List<ReadinessAssessmentItem> items = new ArrayList<>();
        for (ReadinessChecklistItem item : checklist.items()) {
            ReadinessCommands.AssessmentAnswer answer = byCode.remove(item.itemCode());
            // An unanswered item counts as failed. A checklist an assessor skipped half of is not a
            // pass, and defaulting the other way would let a hall go ready on an empty submission.
            boolean passed = answer != null && answer.passed();
            String comment = answer == null ? "Not answered" : answer.comment();
            items.add(ReadinessAssessmentItem.answered(assessmentId, item, passed, comment));
        }
        if (!byCode.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Unknown checklist item code(s) for " + checklist.checklistCode() + ": "
                            + String.join(", ", byCode.keySet()));
        }
        return items;
    }

    private List<ReadinessChecklistItem> toItems(UUID checklistId,
            List<ReadinessCommands.ChecklistItem> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A readiness checklist must contain at least one item.");
        }
        List<ReadinessChecklistItem> items = new ArrayList<>();
        int order = 0;
        for (ReadinessCommands.ChecklistItem item : requested) {
            order += 10;
            items.add(ReadinessChecklistItem.of(checklistId, item.itemCode(), item.description(),
                    item.severityIfFailed(), item.mandatory() == null || item.mandatory(), item.weight(),
                    item.sortOrder() == null ? order : item.sortOrder()));
        }
        return items;
    }

    OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    ReadinessChecklist requireChecklist(UUID id) {
        return readiness.findChecklist(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Readiness checklist", id));
    }

    FacilityRoom requireRoom(UUID id) {
        return facilities.findRoom(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Space", id));
    }

    void publish(String eventType, String aggregateType, UUID aggregateId, String siteScope,
            ActorContext actor, Object payload) {
        outbox.record(eventType, 1, aggregateType, aggregateId, siteScope, actor.correlationId(),
                actor.actorId(), payload);
    }

    Instant now() {
        return clock.instant();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A code is required.");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
