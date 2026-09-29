package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.ScheduleOccurrencePolicy;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routine schedules, checklist templates and the generation sweep - SRS-SFL-S169-01 and -02.
 *
 * <h2>Generation is idempotent by construction</h2>
 *
 * The sweep materialises every occurrence from now to the configured horizon for every active schedule
 * and every room it covers. A second run finds each occurrence already has its task
 * ({@link CleaningRepository#existsOccurrence}) and writes nothing; the unique index
 * {@code ux_cleaning_tasks_schedule_occurrence} is the guarantee behind that check, and the generation
 * advisory lock makes two instances queue rather than race to the index. So the interval is a latency
 * choice - how soon a new room or schedule shows up - not a correctness one.
 */
@Service
public class CleaningScheduleService {

    /** What one sweep did, for the scheduler's log line and the on-demand endpoint's response. */
    public record GenerationResult(int schedulesExamined, int tasksCreated, int alreadyPresent, Instant from,
            Instant to) {
    }

    private final CleaningSupport support;
    private final CleaningRepository repository;

    public CleaningScheduleService(CleaningSupport support) {
        this.support = support;
        this.repository = support.repository();
    }

    // ---- schedules ----------------------------------------------------------------------------

    @Transactional
    public CleaningSchedule create(CleaningCommands.CreateSchedule command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_SCHEDULE_MANAGE, site,
                command.channel(), "CleaningSchedule", "new");
        if (support.facilities().findSiteByCode(site).isEmpty()) {
            throw new FacilitiesException.InvalidParentReferenceException("Site", site);
        }
        if (command.roomId() != null) {
            FacilityRoom room = support.requireRoom(command.roomId());
            if (!room.siteCode().equals(site)) {
                throw new FacilitiesException.ValidationFailedException(
                        room.roomCode() + " is at " + room.siteCode() + ", not " + site + ".");
            }
            if (room.spaceType() != command.spaceType()) {
                throw new FacilitiesException.ValidationFailedException(
                        room.roomCode() + " is a " + room.spaceType() + ", not a " + command.spaceType() + ".");
            }
        }
        CleaningSchedule schedule = repository.saveSchedule(CleaningSchedule.create(UUID.randomUUID(), site,
                command.name(), command.spaceType(), command.roomId(), command.frequency(), command.daysOfWeek(),
                command.timesOfDay(), command.durationMinutes(), actor.actorId(), support.now(), command.channel(),
                actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_SCHEDULE_CREATED, "CleaningSchedule",
                schedule.id().toString(), schedule.siteCode(), null, schedule);
        return schedule;
    }

    @Transactional
    public CleaningSchedule update(CleaningCommands.UpdateSchedule command) {
        ActorContext actor = command.actor();
        CleaningSchedule existing = repository.findSchedule(command.scheduleId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Cleaning schedule",
                        command.scheduleId()));
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_SCHEDULE_MANAGE, existing.siteCode(),
                command.channel(), "CleaningSchedule", existing.id().toString());
        existing.metadata().requireVersion(command.expectedVersion(), "Cleaning schedule", existing.id());
        CleaningSchedule updated = repository.saveSchedule(existing.update(command.name(), command.frequency(),
                command.daysOfWeek(), command.timesOfDay(), command.durationMinutes(), command.active(),
                actor.actorId(), support.now(), command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_SCHEDULE_UPDATED, "CleaningSchedule",
                updated.id().toString(), updated.siteCode(), existing, updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public List<CleaningSchedule> schedules(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.requireUnnarrowedRead(actor, site, channel, "CleaningSchedule");
        support.authorization().requireSite(actor, site, channel, "CleaningSchedule", "list");
        return support.authorization().filterBySite(actor, repository.findSchedules(site), CleaningSchedule::siteCode);
    }

    // ---- checklist templates ------------------------------------------------------------------

    /** A new version for the site and space type, superseding the active one in the same transaction. */
    @Transactional
    public ChecklistTemplate createTemplate(CleaningCommands.CreateTemplate command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_SCHEDULE_MANAGE, site,
                command.channel(), "CleaningChecklistTemplate", "new");
        if (command.items() == null || command.items().isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("A checklist template needs at least one item.");
        }
        List<ChecklistTemplate.Item> items = new ArrayList<>();
        int sequence = 1;
        for (CleaningCommands.ChecklistItemSpec spec : command.items()) {
            items.add(new ChecklistTemplate.Item(UUID.randomUUID(), spec.itemCode(), spec.label(), sequence++,
                    spec.photoRequired()));
        }
        Instant at = support.now();
        int version = repository.latestTemplateVersion(site, command.spaceType()) + 1;
        ChecklistTemplate candidate = ChecklistTemplate.create(UUID.randomUUID(), site, command.spaceType(),
                command.name(), version, items, actor.actorId(), at, command.channel(), actor.correlationId());
        Optional<ChecklistTemplate> previous = repository.findActiveTemplate(site, command.spaceType());
        previous.ifPresent(old -> repository.saveTemplate(old.supersede(actor.actorId(), at, command.channel(),
                actor.correlationId())));
        ChecklistTemplate saved = repository.saveTemplate(candidate);
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_CHECKLIST_TEMPLATE_CREATED,
                "CleaningChecklistTemplate", saved.id().toString(), saved.siteCode(), previous.orElse(null), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChecklistTemplate> templates(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_READ, site, channel,
                "CleaningChecklistTemplate", "list");
        return repository.findTemplates(site);
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * Materialises routine tasks from now to the horizon.
     *
     * @param siteCode one site (the on-demand endpoint), or null for every active schedule (the sweep)
     */
    @Transactional
    public GenerationResult generate(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        if (site != null) {
            support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_SCHEDULE_MANAGE, site, channel,
                    "CleaningSchedule", "generation");
        }
        repository.lockGeneration();
        Instant from = support.now();
        Instant latestTo = from;
        int examined = 0;
        int created = 0;
        int present = 0;
        List<CleaningSchedule> schedules = site == null ? repository.findActiveSchedules()
                : repository.findSchedules(site).stream().filter(CleaningSchedule::active).toList();
        for (CleaningSchedule schedule : schedules) {
            examined++;
            ZoneId zone = support.configuration().timeZone(schedule.siteCode());
            Instant to = from.plus(support.configuration().horizon(schedule.siteCode()));
            latestTo = to.isAfter(latestTo) ? to : latestTo;
            List<FacilityRoom> rooms = roomsCoveredBy(schedule);
            for (Instant occurrence : ScheduleOccurrencePolicy.occurrences(schedule, zone, from, to)) {
                for (FacilityRoom room : rooms) {
                    if (repository.existsOccurrence(schedule.id(), room.id(), occurrence)) {
                        present++;
                        continue;
                    }
                    support.raise(room, TaskOrigin.ROUTINE, schedule.name() + " - " + room.roomCode(), null,
                            schedule.id(), occurrence, null, null, null, null, occurrence,
                            occurrence.plusSeconds(schedule.durationMinutes() * 60L), actor.actorId(), actor,
                            channel);
                    created++;
                }
            }
        }
        return new GenerationResult(examined, created, present, from, latestTo);
    }

    private List<FacilityRoom> roomsCoveredBy(CleaningSchedule schedule) {
        if (schedule.roomId() != null) {
            return support.facilities().findRoom(schedule.roomId())
                    .filter(room -> room.lifecycleStatus().isOperational()).map(List::of).orElse(List.of());
        }
        return support.facilities().findActiveRooms(schedule.siteCode()).stream()
                .filter(room -> schedule.covers(room.id(), room.spaceType()))
                .toList();
    }
}
