package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.VendorMasterPort;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cleaning vendors, their SLA terms and their scorecards - SRS-SFL-S169-03.
 *
 * <h2>Vendor Master is not integrated, and this class does not pretend it is</h2>
 *
 * Registration resolves the reference through {@link VendorMasterPort}. The adapter behind it resolves
 * only references an authorised user has recorded as known to S133 - S133 itself is not built - and
 * {@link #vendorMasterStatus()} says so to anybody who asks. A reference that does not resolve is
 * {@code CLEANING_VENDOR_NOT_FOUND}, the one error state the SRS names for this requirement.
 *
 * <h2>The scorecard is computed, never entered</h2>
 *
 * Every number on it is derived: tasks from the task table, breaches from what {@link SlaEvaluator}
 * recorded against the tasks' own timestamps and the occupants' ratings. There is no endpoint through
 * which a vendor, or anybody else, can write a compliance figure.
 */
@Service
public class CleaningVendorService {

    /** The default scorecard period when the caller gives none. */
    static final Duration DEFAULT_PERIOD = Duration.ofDays(90);

    public record Scorecard(
            CleaningVendor vendor,
            VendorSlaTerms currentTerms,
            Instant from,
            Instant to,
            int tasksAssigned,
            int tasksCompleted,
            int responseBreaches,
            int completionBreaches,
            int qualityBreaches,
            int completedWithoutBreach,
            BigDecimal compliancePercent,
            int ratings,
            BigDecimal averageRating,
            int completionDiscrepancies,
            List<SlaBreach> breaches,
            String vendorMasterStatus) {
    }

    /** One run of the response sweep. */
    public record SlaSweep(int examined, int breachesRecorded, Instant evaluatedAt) {
    }

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final VendorMasterPort vendorMaster;
    private final SlaEvaluator sla;

    public CleaningVendorService(CleaningSupport support, VendorMasterPort vendorMaster, SlaEvaluator sla) {
        this.support = support;
        this.repository = support.repository();
        this.vendorMaster = vendorMaster;
        this.sla = sla;
    }

    // ---- the Vendor Master stand-in -----------------------------------------------------------

    /** Records a reference as known to S133, pending a real integration. Vendor managers only. */
    @Transactional
    public VendorMasterPort.VendorMasterRecord recordMasterReference(
            CleaningCommands.RecordVendorMasterReference command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_VENDOR_MANAGE, site,
                command.channel(), "CleaningVendorMasterReference", "new");
        if (command.reference() == null || command.reference().isBlank() || command.legalName() == null
                || command.legalName().isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A Vendor Master reference and the vendor's legal name are both required.");
        }
        VendorMasterPort.VendorMasterRecord recorded = vendorMaster.recordKnownReference(site, command.reference(),
                command.legalName(), command.evidenceNote(), actor.actorId(), command.channel(), actor.correlationId());
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_VENDOR_MASTER_REFERENCE_RECORDED,
                "CleaningVendorMasterReference", recorded.reference(), site, null,
                Map.of("reference", recorded.reference(), "legalName", recorded.legalName(),
                        "integrationStatus", vendorMaster.integrationStatus()));
        return recorded;
    }

    @Transactional(readOnly = true)
    public List<VendorMasterPort.VendorMasterRecord> knownReferences(String siteCode, ActorContext actor,
            SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_VENDOR_MANAGE, site, channel,
                "CleaningVendorMasterReference", "list");
        return vendorMaster.knownReferences(site);
    }

    public String vendorMasterStatus() {
        return vendorMaster.integrationStatus();
    }

    // ---- vendors ------------------------------------------------------------------------------

    @Transactional
    public CleaningVendor register(CleaningCommands.RegisterVendor command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_VENDOR_MANAGE, site,
                command.channel(), "CleaningVendor", "new");
        if (command.vendorMasterReference() == null || command.vendorMasterReference().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A Vendor Master (S133) reference is required.");
        }
        VendorMasterPort.VendorMasterRecord master = vendorMaster.resolve(site, command.vendorMasterReference())
                .filter(VendorMasterPort.VendorMasterRecord::active)
                .orElseThrow(() -> new FacilitiesException(FacilitiesErrorCode.CLEANING_VENDOR_NOT_FOUND,
                        FacilitiesErrorCode.CLEANING_VENDOR_NOT_FOUND.defaultMessage() + " Reference: "
                                + command.vendorMasterReference().strip() + ". " + vendorMaster.integrationStatus()));
        if (repository.findVendorByReference(site, master.reference()).isPresent()) {
            throw new FacilitiesException.DuplicateIdentifierException("cleaning vendor", master.reference(), site);
        }
        CleaningVendor vendor = repository.saveVendor(CleaningVendor.register(UUID.randomUUID(), site,
                master.reference(), master.legalName(), actor.actorId(), support.now(), command.channel(),
                actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_VENDOR_REGISTERED, "CleaningVendor",
                vendor.id().toString(), vendor.siteCode(), null, vendor);
        return vendor;
    }

    @Transactional
    public CleaningVendor changeStatus(CleaningCommands.ChangeVendorStatus command) {
        ActorContext actor = command.actor();
        CleaningVendor vendor = requireVendor(command.vendorId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_VENDOR_MANAGE, vendor.siteCode(),
                command.channel(), "CleaningVendor", vendor.id().toString());
        CleaningVendor changed = repository.saveVendor(vendor.withStatus(command.status(), actor.actorId(),
                support.now(), command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_VENDOR_STATUS_CHANGED, "CleaningVendor",
                changed.id().toString(), changed.siteCode(), vendor, changed);
        return changed;
    }

    @Transactional(readOnly = true)
    public List<CleaningVendor> vendors(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.requireUnnarrowedRead(actor, site, channel, "CleaningVendor");
        support.authorization().requireSite(actor, site, channel, "CleaningVendor", "list");
        return support.authorization().filterBySite(actor, repository.findVendors(site), CleaningVendor::siteCode);
    }

    // ---- SLA terms ----------------------------------------------------------------------------

    /** A new version of the contracted terms, closing the one in force. */
    @Transactional
    public VendorSlaTerms setTerms(CleaningCommands.SetSlaTerms command) {
        ActorContext actor = command.actor();
        CleaningVendor vendor = requireVendor(command.vendorId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_VENDOR_MANAGE, vendor.siteCode(),
                command.channel(), "CleaningVendorSlaTerms", vendor.id().toString());
        Instant at = support.now();
        List<VendorSlaTerms> history = repository.findTermsHistory(vendor.id());
        VendorSlaTerms candidate = VendorSlaTerms.create(UUID.randomUUID(), vendor.siteCode(), vendor.id(),
                history.size() + 1, command.responseMinutes(), command.completionMinutes(), command.qualityFloor(),
                actor.actorId(), at, command.channel(), actor.correlationId());
        Optional<VendorSlaTerms> current = repository.findCurrentTerms(vendor.id());
        current.ifPresent(old -> repository.saveTerms(old.close(actor.actorId(), at, command.channel(),
                actor.correlationId())));
        VendorSlaTerms saved = repository.saveTerms(candidate);
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_VENDOR_SLA_VERSION_CREATED,
                "CleaningVendorSlaTerms", saved.id().toString(), saved.siteCode(), current.orElse(null), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<VendorSlaTerms> termsHistory(UUID vendorId, ActorContext actor, SourceChannel channel) {
        CleaningVendor vendor = requireVendor(vendorId);
        support.requireUnnarrowedRead(actor, vendor.siteCode(), channel, "CleaningVendorSlaTerms");
        support.authorization().requireSite(actor, vendor.siteCode(), channel, "CleaningVendorSlaTerms",
                vendorId.toString());
        return repository.findTermsHistory(vendorId);
    }

    // ---- scorecard ----------------------------------------------------------------------------

    /**
     * The vendor's scorecard over {@code [from, to)} - "so that a persistently underperforming vendor is
     * visible before the contract renews" (SRS-SFL-S169-03 user story).
     *
     * <p>Compliance is completed tasks with no breach of any type, over completed tasks. A task still
     * open does not count either way, except that its response breach, if it has one, is listed.
     */
    @Transactional(readOnly = true)
    public Scorecard scorecard(UUID vendorId, Instant from, Instant to, ActorContext actor, SourceChannel channel) {
        CleaningVendor vendor = requireVendor(vendorId);
        support.requireUnnarrowedRead(actor, vendor.siteCode(), channel, "CleaningVendorScorecard");
        support.authorization().requireSite(actor, vendor.siteCode(), channel, "CleaningVendorScorecard",
                vendorId.toString());
        Instant end = to == null ? support.now() : to;
        Instant start = from == null ? end.minus(DEFAULT_PERIOD) : from;
        return computeScorecard(vendor, start, end);
    }

    Scorecard computeScorecard(CleaningVendor vendor, Instant start, Instant end) {
        List<CleaningTask> tasks = repository.findVendorTasks(vendor.id(), start, end);
        Set<UUID> taskIds = tasks.stream().map(CleaningTask::id).collect(Collectors.toSet());
        List<SlaBreach> breaches = repository.findBreachesForVendor(vendor.id(), start, end.plus(DEFAULT_PERIOD))
                .stream().filter(breach -> taskIds.contains(breach.taskId())).toList();
        List<CleaningTask> completed = tasks.stream().filter(task -> task.status() == TaskStatus.COMPLETED).toList();
        Set<UUID> breachedTasks = breaches.stream().map(SlaBreach::taskId).collect(Collectors.toSet());
        int compliant = (int) completed.stream().filter(task -> !breachedTasks.contains(task.id())).count();
        BigDecimal compliance = completed.isEmpty() ? null
                : BigDecimal.valueOf(compliant * 100L).divide(BigDecimal.valueOf(completed.size()), 1,
                        RoundingMode.HALF_UP);
        List<CleaningFeedback> feedback = repository.findFeedbackForVendor(vendor.id(), start, end.plus(DEFAULT_PERIOD))
                .stream().filter(item -> taskIds.contains(item.taskId())).toList();
        BigDecimal average = feedback.isEmpty() ? null
                : BigDecimal.valueOf(feedback.stream().mapToInt(CleaningFeedback::rating).sum())
                        .divide(BigDecimal.valueOf(feedback.size()), 2, RoundingMode.HALF_UP);
        return new Scorecard(vendor, repository.findCurrentTerms(vendor.id()).orElse(null), start, end, tasks.size(),
                completed.size(), count(breaches, SlaBreachType.RESPONSE), count(breaches, SlaBreachType.COMPLETION),
                count(breaches, SlaBreachType.QUALITY), compliant, compliance, feedback.size(), average,
                (int) tasks.stream().filter(task -> task.completionDiscrepancySeconds() != null).count(), breaches,
                vendorMaster.integrationStatus());
    }

    // ---- the response sweep -------------------------------------------------------------------

    /**
     * Records a response breach on every unattended reactive vendor request past its contracted
     * response time, without waiting for somebody to start it - an unattended request is the breach.
     */
    @Transactional
    public SlaSweep sweepResponse(ActorContext actor) {
        List<CleaningTask> candidates = repository.findUnstartedReactiveVendorTasks(support.configuration()
                .sweepBatch(null));
        int recorded = 0;
        for (CleaningTask task : candidates) {
            recorded += sla.evaluateTimes(task, actor, SourceChannel.SCHEDULER).size();
        }
        return new SlaSweep(candidates.size(), recorded, support.now());
    }

    private static int count(List<SlaBreach> breaches, SlaBreachType type) {
        return (int) breaches.stream().filter(breach -> breach.type() == type).count();
    }

    CleaningVendor requireVendor(UUID vendorId) {
        return repository.findVendor(vendorId).orElseThrow(() -> new FacilitiesException(
                FacilitiesErrorCode.CLEANING_VENDOR_NOT_FOUND, "No cleaning vendor " + vendorId + " is registered."));
    }
}
