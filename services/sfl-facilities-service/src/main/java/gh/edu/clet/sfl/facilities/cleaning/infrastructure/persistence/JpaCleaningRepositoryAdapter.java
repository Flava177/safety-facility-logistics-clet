package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CapacityReservation;
import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.cleaning.domain.ReservationStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link CleaningRepository}.
 *
 * <p>Writes reuse the managed row and check the version basis first, the {@link
 * gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord} pattern, so a stale edit
 * is a {@code VERSION_CONFLICT} rather than a silent overwrite. Task saves flush, so a unique-index
 * violation (a duplicated occurrence, a second live booking clean) surfaces inside the command that
 * caused it rather than at commit.
 *
 * <p>Temporal filters are never passed as null, for the reason {@code JpaBookingRepositoryAdapter}
 * gives: PostgreSQL cannot type an untyped null {@code timestamptz} parameter.
 */
@Repository
public class JpaCleaningRepositoryAdapter implements CleaningRepository {

    private static final Instant UNBOUNDED_FROM = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T00:00:00Z");
    private static final Set<TaskStatus> LIVE = EnumSet.of(TaskStatus.OPEN, TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS);

    /** Advisory-lock namespaces; arbitrary and fixed, distinct from booking's. */
    private static final long CAPACITY_LOCK_NAMESPACE = 0x5346_4C43_4150_4143L;
    private static final long GENERATION_LOCK_KEY = 0x5346_4C43_4C4E_4745L;

    private final JpaCleaningTaskRepository tasks;
    private final JpaCleaningChecklistItemRepository items;
    private final JpaCleaningScheduleRepository schedules;
    private final JpaCleaningTemplateRepository templates;
    private final JpaCleaningTemplateItemRepository templateItems;
    private final JpaCleaningFeedbackRepository feedback;
    private final JpaCleaningFlagRepository flags;
    private final JpaCleaningVendorRepository vendors;
    private final JpaCleaningSlaTermsRepository terms;
    private final JpaCleaningBreachRepository breaches;
    private final JpaCleaningReservationRepository reservations;
    private final long lockTimeoutMillis;

    public JpaCleaningRepositoryAdapter(JpaCleaningTaskRepository tasks, JpaCleaningChecklistItemRepository items,
            JpaCleaningScheduleRepository schedules, JpaCleaningTemplateRepository templates,
            JpaCleaningTemplateItemRepository templateItems, JpaCleaningFeedbackRepository feedback,
            JpaCleaningFlagRepository flags, JpaCleaningVendorRepository vendors, JpaCleaningSlaTermsRepository terms,
            JpaCleaningBreachRepository breaches, JpaCleaningReservationRepository reservations,
            @Value("${sfl.cleaning.advisory-lock-timeout:PT5S}") Duration lockTimeout) {
        this.tasks = tasks;
        this.items = items;
        this.schedules = schedules;
        this.templates = templates;
        this.templateItems = templateItems;
        this.feedback = feedback;
        this.flags = flags;
        this.vendors = vendors;
        this.terms = terms;
        this.breaches = breaches;
        this.reservations = reservations;
        this.lockTimeoutMillis = lockTimeout.toMillis();
    }

    // ---- concurrency --------------------------------------------------------------------------

    @Override
    public void lockCapacity(String siteCode) {
        lock(normalize(siteCode).hashCode() ^ CAPACITY_LOCK_NAMESPACE);
    }

    @Override
    public void lockGeneration() {
        lock(GENERATION_LOCK_KEY);
    }

    private void lock(long key) {
        try {
            tasks.acquireAdvisoryLock(key, lockTimeoutMillis);
        } catch (DataAccessException failure) {
            if (mentions(failure, "lock timeout")) {
                throw new FacilitiesException(
                        gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.CLEANING_RESOURCING_CONFLICT,
                        "Another cleaning capacity request at this site is in progress; timed out waiting. Try again.");
            }
            throw failure;
        }
    }

    // ---- tasks --------------------------------------------------------------------------------

    @Override
    public String nextTaskNumber(String siteCode) {
        return "CT-" + normalize(siteCode) + "-" + String.format("%06d", tasks.nextTaskSequence());
    }

    @Override
    public CleaningTask saveTask(CleaningTask task) {
        Optional<CleaningTaskRecord> existing = tasks.findById(task.id());
        existing.ifPresent(record -> record.requireNotStale(task.metadata().version()));
        CleaningTaskRecord record = existing.orElseGet(CleaningTaskRecord::empty);
        record.apply(task);
        return tasks.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<CleaningTask> findTask(UUID id) {
        return id == null ? Optional.empty() : tasks.findById(id).map(CleaningTaskRecord::toDomain);
    }

    @Override
    public boolean existsOccurrence(UUID scheduleId, UUID roomId, Instant occurrenceStart) {
        return tasks.existsOccurrence(scheduleId, roomId, occurrenceStart);
    }

    @Override
    public List<CleaningTask> findTasksForBooking(UUID bookingId) {
        return tasks.findForBooking(bookingId).stream().map(CleaningTaskRecord::toDomain).toList();
    }

    @Override
    public RepositoryPage<CleaningTask> findTasks(TaskQuery query) {
        Page<CleaningTaskRecord> page = tasks.search(normalizeOrNull(query.siteCode()), query.roomId(), query.status(),
                query.origin(), query.requestedBy(), query.assignedTo(), query.bookingId(), from(query.from()),
                to(query.to()), PageRequest.of(Math.max(0, query.page()), clamp(query.size())));
        return RepositoryPage.of(page.getContent().stream().map(CleaningTaskRecord::toDomain).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Override
    public List<CleaningTask> findLiveTasksOverlapping(String siteCode, Instant from, Instant to) {
        return tasks.findOverlapping(normalize(siteCode), LIVE, from, to).stream().map(CleaningTaskRecord::toDomain)
                .toList();
    }

    @Override
    public List<CleaningTask> findUnstartedReactiveVendorTasks(int limit) {
        return tasks.findUnstartedReactiveVendor(PageRequest.of(0, clamp(limit))).stream()
                .map(CleaningTaskRecord::toDomain).toList();
    }

    @Override
    public List<CleaningTask> findTasksStartingBetween(String siteCode, Instant from, Instant to) {
        return tasks.findStartingBetween(normalize(siteCode), from, to).stream().map(CleaningTaskRecord::toDomain)
                .toList();
    }

    @Override
    public List<CleaningTask> findOverdueReactive(String siteCode, Instant now, int limit) {
        return tasks.findOverdueReactive(normalize(siteCode), LIVE, now, PageRequest.of(0, clamp(limit))).stream()
                .map(CleaningTaskRecord::toDomain).toList();
    }

    @Override
    public List<CleaningTask> findVendorTasks(UUID vendorId, Instant from, Instant to) {
        return tasks.findVendorTasks(vendorId, from, to).stream().map(CleaningTaskRecord::toDomain).toList();
    }

    // ---- task checklist -----------------------------------------------------------------------

    @Override
    public TaskChecklistItem saveChecklistItem(TaskChecklistItem item) {
        Optional<CleaningTaskChecklistItemRecord> existing = items.findById(item.id());
        existing.ifPresent(record -> record.requireNotStale(item.metadata().version()));
        CleaningTaskChecklistItemRecord record = existing.orElseGet(CleaningTaskChecklistItemRecord::empty);
        record.apply(item);
        return items.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<TaskChecklistItem> findChecklistItem(UUID id) {
        return id == null ? Optional.empty() : items.findById(id).map(CleaningTaskChecklistItemRecord::toDomain);
    }

    @Override
    public List<TaskChecklistItem> findChecklistItems(UUID taskId) {
        return items.findForTask(taskId).stream().map(CleaningTaskChecklistItemRecord::toDomain).toList();
    }

    // ---- schedules and templates --------------------------------------------------------------

    @Override
    public CleaningSchedule saveSchedule(CleaningSchedule schedule) {
        Optional<CleaningScheduleRecord> existing = schedules.findById(schedule.id());
        existing.ifPresent(record -> record.requireNotStale(schedule.metadata().version()));
        CleaningScheduleRecord record = existing.orElseGet(CleaningScheduleRecord::empty);
        record.apply(schedule);
        return schedules.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<CleaningSchedule> findSchedule(UUID id) {
        return schedules.findById(id).map(CleaningScheduleRecord::toDomain);
    }

    @Override
    public List<CleaningSchedule> findSchedules(String siteCode) {
        return schedules.findForSite(normalize(siteCode)).stream().map(CleaningScheduleRecord::toDomain).toList();
    }

    @Override
    public List<CleaningSchedule> findActiveSchedules() {
        return schedules.findActive().stream().map(CleaningScheduleRecord::toDomain).toList();
    }

    /** The header is versioned; items are written with a new template and never changed after. */
    @Override
    public ChecklistTemplate saveTemplate(ChecklistTemplate template) {
        Optional<CleaningChecklistTemplateRecord> existing = templates.findById(template.id());
        existing.ifPresent(record -> record.requireNotStale(template.metadata().version()));
        CleaningChecklistTemplateRecord record = existing.orElseGet(CleaningChecklistTemplateRecord::empty);
        record.apply(template);
        CleaningChecklistTemplateRecord saved = templates.saveAndFlush(record);
        if (existing.isEmpty()) {
            for (ChecklistTemplate.Item item : template.items()) {
                templateItems.save(CleaningChecklistTemplateItemRecord.of(template, item, template.metadata()));
            }
            templateItems.flush();
        }
        return withItems(saved);
    }

    @Override
    public Optional<ChecklistTemplate> findTemplate(UUID id) {
        return templates.findById(id).map(this::withItems);
    }

    @Override
    public Optional<ChecklistTemplate> findActiveTemplate(String siteCode, SpaceType spaceType) {
        return templates.findActive(normalize(siteCode), spaceType).map(this::withItems);
    }

    @Override
    public int latestTemplateVersion(String siteCode, SpaceType spaceType) {
        return templates.latestVersion(normalize(siteCode), spaceType);
    }

    @Override
    public List<ChecklistTemplate> findTemplates(String siteCode) {
        return templates.findForSite(normalize(siteCode)).stream().map(this::withItems).toList();
    }

    private ChecklistTemplate withItems(CleaningChecklistTemplateRecord record) {
        return record.toDomain(templateItems.findForTemplate(record.getId()).stream()
                .map(CleaningChecklistTemplateItemRecord::toDomain).toList());
    }

    // ---- feedback and flags -------------------------------------------------------------------

    @Override
    public CleaningFeedback saveFeedback(CleaningFeedback item) {
        CleaningFeedbackRecord record = feedback.findById(item.id()).orElseGet(CleaningFeedbackRecord::empty);
        record.apply(item);
        return feedback.saveAndFlush(record).toDomain();
    }

    @Override
    public boolean existsFeedback(UUID taskId, String submittedBy) {
        return feedback.exists(taskId, submittedBy);
    }

    @Override
    public List<CleaningFeedback> findFeedbackForTask(UUID taskId) {
        return feedback.findForTask(taskId).stream().map(CleaningFeedbackRecord::toDomain).toList();
    }

    @Override
    public List<CleaningFeedback> findFeedback(String siteCode, Instant from, Instant to) {
        return feedback.findForSite(normalize(siteCode), from(from), to(to)).stream()
                .map(CleaningFeedbackRecord::toDomain).toList();
    }

    @Override
    public List<CleaningFeedback> findFeedbackForVendor(UUID vendorId, Instant from, Instant to) {
        return feedback.findForVendor(vendorId, from(from), to(to)).stream().map(CleaningFeedbackRecord::toDomain)
                .toList();
    }

    @Override
    public long countLowRatingsForRoom(UUID roomId, int maxRating, Instant since) {
        return feedback.countLowForRoom(roomId, maxRating, since);
    }

    @Override
    public long countLowRatingsForVendor(UUID vendorId, int maxRating, Instant since) {
        return feedback.countLowForVendor(vendorId, maxRating, since);
    }

    @Override
    public LowRatingFlag saveFlag(LowRatingFlag flag) {
        Optional<CleaningLowRatingFlagRecord> existing = flags.findById(flag.id());
        existing.ifPresent(record -> record.requireNotStale(flag.metadata().version()));
        CleaningLowRatingFlagRecord record = existing.orElseGet(CleaningLowRatingFlagRecord::empty);
        record.apply(flag);
        return flags.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<LowRatingFlag> findFlag(UUID id) {
        return flags.findById(id).map(CleaningLowRatingFlagRecord::toDomain);
    }

    @Override
    public Optional<LowRatingFlag> findOpenFlag(String siteCode, FlagSubjectType type, UUID subjectId) {
        return flags.findOpen(normalize(siteCode), type, subjectId).map(CleaningLowRatingFlagRecord::toDomain);
    }

    @Override
    public List<LowRatingFlag> findFlags(String siteCode, Boolean open) {
        String status = open == null ? null
                : open ? CleaningLowRatingFlagRecord.OPEN : CleaningLowRatingFlagRecord.REVIEWED;
        return flags.findForSite(normalize(siteCode), status).stream().map(CleaningLowRatingFlagRecord::toDomain)
                .toList();
    }

    // ---- vendors, SLA terms, breaches ---------------------------------------------------------

    @Override
    public CleaningVendor saveVendor(CleaningVendor vendor) {
        Optional<CleaningVendorRecord> existing = vendors.findById(vendor.id());
        existing.ifPresent(record -> record.requireNotStale(vendor.metadata().version()));
        CleaningVendorRecord record = existing.orElseGet(CleaningVendorRecord::empty);
        record.apply(vendor);
        return vendors.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<CleaningVendor> findVendor(UUID id) {
        return id == null ? Optional.empty() : vendors.findById(id).map(CleaningVendorRecord::toDomain);
    }

    @Override
    public Optional<CleaningVendor> findVendorByReference(String siteCode, String vendorMasterReference) {
        return vendors.findByReference(normalize(siteCode), normalize(vendorMasterReference))
                .map(CleaningVendorRecord::toDomain);
    }

    @Override
    public List<CleaningVendor> findVendors(String siteCode) {
        return vendors.findForSite(normalize(siteCode)).stream().map(CleaningVendorRecord::toDomain).toList();
    }

    @Override
    public VendorSlaTerms saveTerms(VendorSlaTerms slaTerms) {
        Optional<CleaningVendorSlaTermsRecord> existing = terms.findById(slaTerms.id());
        existing.ifPresent(record -> record.requireNotStale(slaTerms.metadata().version()));
        CleaningVendorSlaTermsRecord record = existing.orElseGet(CleaningVendorSlaTermsRecord::empty);
        record.apply(slaTerms);
        return terms.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<VendorSlaTerms> findCurrentTerms(UUID vendorId) {
        return terms.findCurrent(vendorId).map(CleaningVendorSlaTermsRecord::toDomain);
    }

    @Override
    public List<VendorSlaTerms> findTermsHistory(UUID vendorId) {
        return terms.findHistory(vendorId).stream().map(CleaningVendorSlaTermsRecord::toDomain).toList();
    }

    @Override
    public SlaBreach saveBreach(SlaBreach breach) {
        CleaningSlaBreachRecord record = breaches.findById(breach.id()).orElseGet(CleaningSlaBreachRecord::empty);
        record.apply(breach);
        return breaches.saveAndFlush(record).toDomain();
    }

    @Override
    public boolean existsBreach(UUID taskId, SlaBreachType type) {
        return breaches.exists(taskId, type);
    }

    @Override
    public List<SlaBreach> findBreachesForTask(UUID taskId) {
        return breaches.findForTask(taskId).stream().map(CleaningSlaBreachRecord::toDomain).toList();
    }

    @Override
    public List<SlaBreach> findBreachesForVendor(UUID vendorId, Instant from, Instant to) {
        return breaches.findForVendor(vendorId, from(from), to(to)).stream().map(CleaningSlaBreachRecord::toDomain)
                .toList();
    }

    // ---- capacity reservations ----------------------------------------------------------------

    @Override
    public CapacityReservation saveReservation(CapacityReservation reservation) {
        Optional<CleaningCapacityReservationRecord> existing = reservations.findById(reservation.id());
        existing.ifPresent(record -> record.requireNotStale(reservation.metadata().version()));
        CleaningCapacityReservationRecord record = existing.orElseGet(CleaningCapacityReservationRecord::empty);
        record.apply(reservation);
        return reservations.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<CapacityReservation> findReservation(UUID id) {
        return reservations.findById(id).map(CleaningCapacityReservationRecord::toDomain);
    }

    @Override
    public Optional<CapacityReservation> findLiveReservation(String siteCode, String eventReference) {
        return reservations.findByEvent(normalize(siteCode), eventReference, ReservationStatus.RESERVED)
                .map(CleaningCapacityReservationRecord::toDomain);
    }

    @Override
    public List<CapacityReservation> findReservations(String siteCode, Instant from, Instant to) {
        return reservations.findOverlapping(normalize(siteCode), from(from), to(to)).stream()
                .map(CleaningCapacityReservationRecord::toDomain).toList();
    }

    // ---- internals ----------------------------------------------------------------------------

    private static boolean mentions(Throwable failure, String text) {
        Throwable cursor = failure;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(text)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static Instant from(Instant value) {
        return value == null ? UNBOUNDED_FROM : value;
    }

    private static Instant to(Instant value) {
        return value == null ? UNBOUNDED_TO : value;
    }

    private static String normalize(String value) {
        String normalized = normalizeOrNull(value);
        return normalized == null ? "" : normalized;
    }

    private static String normalizeOrNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, 500));
    }
}
