package gh.edu.clet.sfl.facilities.cleaning.application.ports;

import gh.edu.clet.sfl.facilities.cleaning.domain.CapacityReservation;
import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The cleaning module's persistence port - SRS-SFL-S169-01..04.
 *
 * <p>One port for the module, for the reason {@code BookingRepository} gives: a task, its checklist, its
 * feedback, its breaches and its reservation are written in one transaction and read together. Paging
 * is plain parameters; {@code Pageable} is an infrastructure type the application layer must not name.
 *
 * <p>Every list query takes the site as a parameter and implementations filter on it in SQL. The
 * services then filter again through {@code FacilitiesAuthorization.filterBySite}, and row-level
 * security filters a third time underneath - the Phase 1 defect ADR 0007 records was an unfiltered
 * {@code findAll()}, and three layers is what stops the fourth.
 */
public interface CleaningRepository {

    /** Filters for the task search. Any null field means "no filter". {@code from/to} bound the window start. */
    record TaskQuery(
            String siteCode,
            UUID roomId,
            TaskStatus status,
            TaskOrigin origin,
            String requestedBy,
            String assignedTo,
            UUID bookingId,
            Instant from,
            Instant to,
            int page,
            int size) {
    }

    // ---- concurrency --------------------------------------------------------------------------

    /**
     * Serialises capacity decisions at one site for the rest of the caller's transaction. Two S173
     * requests for the same slot must not both read "one crew free" and both reserve it; the second
     * queues and then reads the first's task.
     */
    void lockCapacity(String siteCode);

    /** Serialises the routine generation sweep, so two instances cannot both generate one occurrence. */
    void lockGeneration();

    // ---- tasks --------------------------------------------------------------------------------

    String nextTaskNumber(String siteCode);

    CleaningTask saveTask(CleaningTask task);

    Optional<CleaningTask> findTask(UUID id);

    /** {@code true} when this schedule occurrence already has its task for this room - the sweep's guard. */
    boolean existsOccurrence(UUID scheduleId, UUID roomId, Instant occurrenceStart);

    List<CleaningTask> findTasksForBooking(UUID bookingId);

    RepositoryPage<CleaningTask> findTasks(TaskQuery query);

    /** Live tasks at a site whose window overlaps {@code [from, to)} - the capacity check's input. */
    List<CleaningTask> findLiveTasksOverlapping(String siteCode, Instant from, Instant to);

    /** Live reactive vendor tasks not yet started - the SLA response sweep's input. */
    List<CleaningTask> findUnstartedReactiveVendorTasks(int limit);

    /** Tasks at a site whose window starts in {@code [from, to)} - the dashboard's input. */
    List<CleaningTask> findTasksStartingBetween(String siteCode, Instant from, Instant to);

    /** Live reactive tasks at a site past their due time at {@code now}, most overdue first. */
    List<CleaningTask> findOverdueReactive(String siteCode, Instant now, int limit);

    /** A vendor's tasks raised in {@code [from, to)} - the scorecard's denominator. */
    List<CleaningTask> findVendorTasks(UUID vendorId, Instant from, Instant to);

    // ---- task checklist -----------------------------------------------------------------------

    TaskChecklistItem saveChecklistItem(TaskChecklistItem item);

    Optional<TaskChecklistItem> findChecklistItem(UUID id);

    List<TaskChecklistItem> findChecklistItems(UUID taskId);

    // ---- schedules and templates --------------------------------------------------------------

    CleaningSchedule saveSchedule(CleaningSchedule schedule);

    Optional<CleaningSchedule> findSchedule(UUID id);

    List<CleaningSchedule> findSchedules(String siteCode);

    /** Every active schedule across the estate - the generation sweep runs estate-wide. */
    List<CleaningSchedule> findActiveSchedules();

    ChecklistTemplate saveTemplate(ChecklistTemplate template);

    Optional<ChecklistTemplate> findTemplate(UUID id);

    Optional<ChecklistTemplate> findActiveTemplate(String siteCode, SpaceType spaceType);

    /** The highest version so far for a site and space type, or zero. */
    int latestTemplateVersion(String siteCode, SpaceType spaceType);

    List<ChecklistTemplate> findTemplates(String siteCode);

    // ---- feedback and flags -------------------------------------------------------------------

    CleaningFeedback saveFeedback(CleaningFeedback feedback);

    boolean existsFeedback(UUID taskId, String submittedBy);

    List<CleaningFeedback> findFeedbackForTask(UUID taskId);

    List<CleaningFeedback> findFeedback(String siteCode, Instant from, Instant to);

    List<CleaningFeedback> findFeedbackForVendor(UUID vendorId, Instant from, Instant to);

    long countLowRatingsForRoom(UUID roomId, int maxRating, Instant since);

    long countLowRatingsForVendor(UUID vendorId, int maxRating, Instant since);

    LowRatingFlag saveFlag(LowRatingFlag flag);

    Optional<LowRatingFlag> findFlag(UUID id);

    Optional<LowRatingFlag> findOpenFlag(String siteCode, FlagSubjectType type, UUID subjectId);

    /** @param open null for both */
    List<LowRatingFlag> findFlags(String siteCode, Boolean open);

    // ---- vendors, SLA terms, breaches ---------------------------------------------------------

    CleaningVendor saveVendor(CleaningVendor vendor);

    Optional<CleaningVendor> findVendor(UUID id);

    Optional<CleaningVendor> findVendorByReference(String siteCode, String vendorMasterReference);

    List<CleaningVendor> findVendors(String siteCode);

    VendorSlaTerms saveTerms(VendorSlaTerms terms);

    Optional<VendorSlaTerms> findCurrentTerms(UUID vendorId);

    /** Every version, oldest first. */
    List<VendorSlaTerms> findTermsHistory(UUID vendorId);

    SlaBreach saveBreach(SlaBreach breach);

    boolean existsBreach(UUID taskId, SlaBreachType type);

    List<SlaBreach> findBreachesForTask(UUID taskId);

    List<SlaBreach> findBreachesForVendor(UUID vendorId, Instant from, Instant to);

    // ---- capacity reservations ----------------------------------------------------------------

    CapacityReservation saveReservation(CapacityReservation reservation);

    Optional<CapacityReservation> findReservation(UUID id);

    Optional<CapacityReservation> findLiveReservation(String siteCode, String eventReference);

    List<CapacityReservation> findReservations(String siteCode, Instant from, Instant to);
}
