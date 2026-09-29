package gh.edu.clet.sfl.facilities.support;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory {@link CleaningRepository} for the S169 application tests - the {@code InMemoryBookingRepository}
 * pattern: every query the module makes, reproduced; no simulated concurrency, because a single-threaded
 * map has no race for {@link #lockCapacity} or {@link #lockGeneration} to serialise.
 */
public class InMemoryCleaningRepository implements CleaningRepository {

    private final Map<UUID, CleaningTask> tasks = new LinkedHashMap<>();
    private final Map<UUID, TaskChecklistItem> items = new LinkedHashMap<>();
    private final Map<UUID, CleaningSchedule> schedules = new LinkedHashMap<>();
    private final Map<UUID, ChecklistTemplate> templates = new LinkedHashMap<>();
    private final Map<UUID, CleaningFeedback> feedback = new LinkedHashMap<>();
    private final Map<UUID, LowRatingFlag> flags = new LinkedHashMap<>();
    private final Map<UUID, CleaningVendor> vendors = new LinkedHashMap<>();
    private final Map<UUID, VendorSlaTerms> terms = new LinkedHashMap<>();
    private final Map<UUID, SlaBreach> breaches = new LinkedHashMap<>();
    private final Map<UUID, CapacityReservation> reservations = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public void lockCapacity(String siteCode) {
    }

    @Override
    public void lockGeneration() {
    }

    // ---- tasks ----------------------------------------------------------------------------------

    @Override
    public String nextTaskNumber(String siteCode) {
        return "CT-" + normalize(siteCode) + "-" + String.format("%06d", sequence.incrementAndGet());
    }

    @Override
    public CleaningTask saveTask(CleaningTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<CleaningTask> findTask(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public boolean existsOccurrence(UUID scheduleId, UUID roomId, Instant occurrenceStart) {
        return tasks.values().stream().anyMatch(task -> scheduleId.equals(task.scheduleId())
                && roomId.equals(task.roomId()) && occurrenceStart.equals(task.occurrenceStart()));
    }

    @Override
    public List<CleaningTask> findTasksForBooking(UUID bookingId) {
        return tasks.values().stream().filter(task -> bookingId.equals(task.bookingId()))
                .sorted(Comparator.comparing(CleaningTask::windowStart)).toList();
    }

    @Override
    public RepositoryPage<CleaningTask> findTasks(TaskQuery query) {
        List<CleaningTask> matching = tasks.values().stream()
                .filter(task -> query.siteCode() == null || task.siteCode().equals(normalize(query.siteCode())))
                .filter(task -> query.roomId() == null || task.roomId().equals(query.roomId()))
                .filter(task -> query.status() == null || task.status() == query.status())
                .filter(task -> query.origin() == null || task.origin() == query.origin())
                .filter(task -> query.requestedBy() == null || query.requestedBy().equals(task.requestedBy()))
                .filter(task -> query.assignedTo() == null || query.assignedTo().equals(task.assignedTo()))
                .filter(task -> query.bookingId() == null || query.bookingId().equals(task.bookingId()))
                .filter(task -> query.from() == null || !task.windowStart().isBefore(query.from()))
                .filter(task -> query.to() == null || task.windowStart().isBefore(query.to()))
                .sorted(Comparator.comparing(CleaningTask::windowStart))
                .toList();
        return paginate(matching, query.page(), Math.max(1, query.size()));
    }

    @Override
    public List<CleaningTask> findLiveTasksOverlapping(String siteCode, Instant from, Instant to) {
        return tasks.values().stream()
                .filter(CleaningTask::isLive)
                .filter(task -> task.siteCode().equals(normalize(siteCode)))
                .filter(task -> task.windowStart().isBefore(to) && from.isBefore(task.dueBy()))
                .sorted(Comparator.comparing(CleaningTask::windowStart))
                .toList();
    }

    @Override
    public List<CleaningTask> findUnstartedReactiveVendorTasks(int limit) {
        return tasks.values().stream()
                .filter(task -> task.origin() == gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin.REACTIVE)
                .filter(task -> task.assigneeType() == gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType.VENDOR)
                .filter(task -> task.status() == TaskStatus.ASSIGNED && task.startedAt() == null)
                .sorted(Comparator.comparing(CleaningTask::requestedAt))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<CleaningTask> findTasksStartingBetween(String siteCode, Instant from, Instant to) {
        return tasks.values().stream()
                .filter(task -> task.siteCode().equals(normalize(siteCode)))
                .filter(task -> !task.windowStart().isBefore(from) && task.windowStart().isBefore(to))
                .sorted(Comparator.comparing(CleaningTask::windowStart))
                .toList();
    }

    @Override
    public List<CleaningTask> findOverdueReactive(String siteCode, Instant now, int limit) {
        return tasks.values().stream()
                .filter(CleaningTask::isLive)
                .filter(task -> task.origin() == gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin.REACTIVE)
                .filter(task -> task.siteCode().equals(normalize(siteCode)))
                .filter(task -> task.dueBy().isBefore(now))
                .sorted(Comparator.comparing(CleaningTask::dueBy))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<CleaningTask> findVendorTasks(UUID vendorId, Instant from, Instant to) {
        return tasks.values().stream()
                .filter(task -> vendorId.equals(task.vendorId()))
                .filter(task -> !task.requestedAt().isBefore(from) && task.requestedAt().isBefore(to))
                .sorted(Comparator.comparing(CleaningTask::requestedAt))
                .toList();
    }

    // ---- task checklist ---------------------------------------------------------------------------

    @Override
    public TaskChecklistItem saveChecklistItem(TaskChecklistItem item) {
        items.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<TaskChecklistItem> findChecklistItem(UUID id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<TaskChecklistItem> findChecklistItems(UUID taskId) {
        return items.values().stream().filter(item -> taskId.equals(item.taskId()))
                .sorted(Comparator.comparingInt(TaskChecklistItem::sequence)).toList();
    }

    // ---- schedules and templates --------------------------------------------------------------

    @Override
    public CleaningSchedule saveSchedule(CleaningSchedule schedule) {
        schedules.put(schedule.id(), schedule);
        return schedule;
    }

    @Override
    public Optional<CleaningSchedule> findSchedule(UUID id) {
        return Optional.ofNullable(schedules.get(id));
    }

    @Override
    public List<CleaningSchedule> findSchedules(String siteCode) {
        return schedules.values().stream().filter(schedule -> schedule.siteCode().equals(normalize(siteCode)))
                .sorted(Comparator.comparing(CleaningSchedule::name)).toList();
    }

    @Override
    public List<CleaningSchedule> findActiveSchedules() {
        return schedules.values().stream().filter(CleaningSchedule::active)
                .sorted(Comparator.comparing(CleaningSchedule::siteCode)).toList();
    }

    @Override
    public ChecklistTemplate saveTemplate(ChecklistTemplate template) {
        templates.put(template.id(), template);
        return template;
    }

    @Override
    public Optional<ChecklistTemplate> findTemplate(UUID id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public Optional<ChecklistTemplate> findActiveTemplate(String siteCode, SpaceType spaceType) {
        return templates.values().stream()
                .filter(template -> template.active() && template.siteCode().equals(normalize(siteCode))
                        && template.spaceType() == spaceType)
                .findFirst();
    }

    @Override
    public int latestTemplateVersion(String siteCode, SpaceType spaceType) {
        return templates.values().stream()
                .filter(template -> template.siteCode().equals(normalize(siteCode)) && template.spaceType() == spaceType)
                .mapToInt(ChecklistTemplate::version).max().orElse(0);
    }

    @Override
    public List<ChecklistTemplate> findTemplates(String siteCode) {
        return templates.values().stream().filter(template -> template.siteCode().equals(normalize(siteCode)))
                .sorted(Comparator.comparing(ChecklistTemplate::spaceType).thenComparing(ChecklistTemplate::version))
                .toList();
    }

    // ---- feedback and flags -------------------------------------------------------------------

    @Override
    public CleaningFeedback saveFeedback(CleaningFeedback item) {
        feedback.put(item.id(), item);
        return item;
    }

    @Override
    public boolean existsFeedback(UUID taskId, String submittedBy) {
        return feedback.values().stream()
                .anyMatch(item -> taskId.equals(item.taskId()) && submittedBy.equals(item.submittedBy()));
    }

    @Override
    public List<CleaningFeedback> findFeedbackForTask(UUID taskId) {
        return feedback.values().stream().filter(item -> taskId.equals(item.taskId()))
                .sorted(Comparator.comparing(CleaningFeedback::submittedAt)).toList();
    }

    @Override
    public List<CleaningFeedback> findFeedback(String siteCode, Instant from, Instant to) {
        return feedback.values().stream()
                .filter(item -> item.siteCode().equals(normalize(siteCode)))
                .filter(item -> !item.submittedAt().isBefore(from) && item.submittedAt().isBefore(to))
                .sorted(Comparator.comparing(CleaningFeedback::submittedAt)).toList();
    }

    @Override
    public List<CleaningFeedback> findFeedbackForVendor(UUID vendorId, Instant from, Instant to) {
        return feedback.values().stream()
                .filter(item -> vendorId.equals(item.vendorId()))
                .filter(item -> !item.submittedAt().isBefore(from) && item.submittedAt().isBefore(to))
                .sorted(Comparator.comparing(CleaningFeedback::submittedAt)).toList();
    }

    @Override
    public long countLowRatingsForRoom(UUID roomId, int maxRating, Instant since) {
        return feedback.values().stream()
                .filter(item -> roomId.equals(item.roomId()) && item.rating() <= maxRating
                        && !item.submittedAt().isBefore(since))
                .count();
    }

    @Override
    public long countLowRatingsForVendor(UUID vendorId, int maxRating, Instant since) {
        return feedback.values().stream()
                .filter(item -> vendorId.equals(item.vendorId()) && item.rating() <= maxRating
                        && !item.submittedAt().isBefore(since))
                .count();
    }

    @Override
    public LowRatingFlag saveFlag(LowRatingFlag flag) {
        flags.put(flag.id(), flag);
        return flag;
    }

    @Override
    public Optional<LowRatingFlag> findFlag(UUID id) {
        return Optional.ofNullable(flags.get(id));
    }

    @Override
    public Optional<LowRatingFlag> findOpenFlag(String siteCode, FlagSubjectType type, UUID subjectId) {
        return flags.values().stream()
                .filter(flag -> flag.open() && flag.siteCode().equals(normalize(siteCode))
                        && flag.subjectType() == type && subjectId.equals(flag.subjectId()))
                .findFirst();
    }

    @Override
    public List<LowRatingFlag> findFlags(String siteCode, Boolean open) {
        return flags.values().stream()
                .filter(flag -> flag.siteCode().equals(normalize(siteCode)))
                .filter(flag -> open == null || flag.open() == open)
                .sorted(Comparator.comparing(LowRatingFlag::flaggedAt).reversed())
                .toList();
    }

    // ---- vendors, SLA terms, breaches ---------------------------------------------------------

    @Override
    public CleaningVendor saveVendor(CleaningVendor vendor) {
        vendors.put(vendor.id(), vendor);
        return vendor;
    }

    @Override
    public Optional<CleaningVendor> findVendor(UUID id) {
        return Optional.ofNullable(vendors.get(id));
    }

    @Override
    public Optional<CleaningVendor> findVendorByReference(String siteCode, String vendorMasterReference) {
        return vendors.values().stream()
                .filter(vendor -> vendor.siteCode().equals(normalize(siteCode))
                        && vendor.vendorMasterReference().equals(normalize(vendorMasterReference)))
                .findFirst();
    }

    @Override
    public List<CleaningVendor> findVendors(String siteCode) {
        return vendors.values().stream().filter(vendor -> vendor.siteCode().equals(normalize(siteCode)))
                .sorted(Comparator.comparing(CleaningVendor::name)).toList();
    }

    @Override
    public VendorSlaTerms saveTerms(VendorSlaTerms slaTerms) {
        terms.put(slaTerms.id(), slaTerms);
        return slaTerms;
    }

    @Override
    public Optional<VendorSlaTerms> findCurrentTerms(UUID vendorId) {
        return terms.values().stream().filter(t -> vendorId.equals(t.vendorId()) && t.effectiveTo() == null)
                .findFirst();
    }

    @Override
    public List<VendorSlaTerms> findTermsHistory(UUID vendorId) {
        return terms.values().stream().filter(t -> vendorId.equals(t.vendorId()))
                .sorted(Comparator.comparingInt(VendorSlaTerms::version)).toList();
    }

    @Override
    public SlaBreach saveBreach(SlaBreach breach) {
        breaches.put(breach.id(), breach);
        return breach;
    }

    @Override
    public boolean existsBreach(UUID taskId, SlaBreachType type) {
        return breaches.values().stream().anyMatch(b -> taskId.equals(b.taskId()) && b.type() == type);
    }

    @Override
    public List<SlaBreach> findBreachesForTask(UUID taskId) {
        return breaches.values().stream().filter(b -> taskId.equals(b.taskId()))
                .sorted(Comparator.comparing(SlaBreach::recordedAt)).toList();
    }

    @Override
    public List<SlaBreach> findBreachesForVendor(UUID vendorId, Instant from, Instant to) {
        return breaches.values().stream()
                .filter(b -> vendorId.equals(b.vendorId()))
                .filter(b -> !b.recordedAt().isBefore(from) && b.recordedAt().isBefore(to))
                .sorted(Comparator.comparing(SlaBreach::recordedAt).reversed())
                .toList();
    }

    // ---- capacity reservations ----------------------------------------------------------------

    @Override
    public CapacityReservation saveReservation(CapacityReservation reservation) {
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Optional<CapacityReservation> findReservation(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    @Override
    public Optional<CapacityReservation> findLiveReservation(String siteCode, String eventReference) {
        return reservations.values().stream()
                .filter(r -> r.status() == ReservationStatus.RESERVED && r.siteCode().equals(normalize(siteCode))
                        && r.eventReference().equals(eventReference))
                .findFirst();
    }

    @Override
    public List<CapacityReservation> findReservations(String siteCode, Instant from, Instant to) {
        return reservations.values().stream()
                .filter(r -> r.siteCode().equals(normalize(siteCode)))
                .filter(r -> r.windowFrom().isBefore(to) && from.isBefore(r.windowTo()))
                .sorted(Comparator.comparing(CapacityReservation::windowFrom))
                .toList();
    }

    // ---- internals ----------------------------------------------------------------------------

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static <T> RepositoryPage<T> paginate(List<T> matching, int page, int size) {
        int from = Math.min(page * size, matching.size());
        int to = Math.min(from + size, matching.size());
        return RepositoryPage.of(new ArrayList<>(matching.subList(from, to)), matching.size(), page, size);
    }
}
