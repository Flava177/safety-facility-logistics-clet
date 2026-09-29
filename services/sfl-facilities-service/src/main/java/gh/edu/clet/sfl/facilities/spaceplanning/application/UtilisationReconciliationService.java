package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.SpaceAllocationService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.BookingUtilisationPort;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.policy.UtilisationPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utilisation reconciliation against S159 booking data - SRS-SFL-S158-03.
 *
 * <p>"S159 utilisation data pulled on schedule -> compared to S158 allocation -> persistent gaps surfaced as
 * planning signals." Run by {@code SpacePlanningScheduledJobs} for every site, or on demand.
 *
 * <h2>Every space, not just the booked ones</h2>
 *
 * S159 reports only rooms that had a booking. The room list here is S152's, so a bookable hall nobody
 * booked all week gets a snapshot of zero use and lands on the under-utilisation list - the room most
 * worth knowing about is exactly the one S159 would otherwise be silent on.
 *
 * <h2>Read-only, and what "unavailable" does</h2>
 *
 * S158 reads S159 through {@link BookingUtilisationPort} and writes nothing back. If S159 cannot be read,
 * the run fails with {@code SPACE_UTILISATION_SOURCE_UNAVAILABLE} before touching a snapshot or a signal:
 * missing data is not evidence that a space is empty, and clearing signals on a failed read would hide
 * precisely the rooms the signal list exists to show.
 */
@Service
public class UtilisationReconciliationService {

    static final String EVENT_SIGNAL_RAISED = "sfl.ifimp.utilisation-signal-raised.v1";
    static final String EVENT_SIGNAL_CLEARED = "sfl.ifimp.utilisation-signal-cleared.v1";

    private final SpacePlanningRepository repository;
    private final FacilitiesRepository facilities;
    private final SpaceAllocationService register;
    private final BookingUtilisationPort bookings;
    private final SpacePlanningConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public UtilisationReconciliationService(SpacePlanningRepository repository, FacilitiesRepository facilities,
            SpaceAllocationService register, BookingUtilisationPort bookings, SpacePlanningConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.register = register;
        this.bookings = bookings;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Pulls one complete period for one site, stores a snapshot per space, and raises or clears signals. */
    @Transactional
    public RunSummary reconcile(SpacePlanningCommands.RunReconciliation command) {
        ActorContext actor = command.actor();
        SourceChannel channel = command.channel() == null ? SourceChannel.SCHEDULER : command.channel();
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_MANAGE, command.siteCode(), channel,
                "UtilisationRun", "reconcile");
        String siteCode = command.siteCode().strip().toUpperCase(Locale.ROOT);
        int periodDays = configuration.periodDays(siteCode);
        Instant now = clock.instant();
        UtilisationPolicy.Period period = periodFor(command.periodEnd(), periodDays, now);

        List<BookingUtilisationPort.ObservedUtilisation> observed;
        try {
            observed = bookings.utilisation(siteCode, period.start(), period.end());
        } catch (RuntimeException unavailable) {
            throw new FacilitiesException(FacilitiesErrorCode.SPACE_UTILISATION_SOURCE_UNAVAILABLE,
                    FacilitiesErrorCode.SPACE_UTILISATION_SOURCE_UNAVAILABLE.defaultMessage() + " ("
                            + unavailable.getClass().getSimpleName() + ")");
        }
        Map<UUID, BookingUtilisationPort.ObservedUtilisation> byRoom = observed.stream()
                .collect(Collectors.toMap(BookingUtilisationPort.ObservedUtilisation::roomId, Function.identity(),
                        (first, second) -> first));
        Map<UUID, Integer> planned = register.current(siteCode, actor, channel).stream()
                .collect(Collectors.groupingBy(SpaceAllocation::roomId,
                        Collectors.summingInt(SpaceAllocation::headcount)));

        Set<DayOfWeek> days = configuration.availableDays(siteCode);
        long availableMinutes = UtilisationPolicy.availableMinutes(period, days,
                configuration.availableHoursPerDay(siteCode));
        BigDecimal underThreshold = configuration.underUtilisedThreshold(siteCode);
        BigDecimal gapThreshold = configuration.gapThreshold(siteCode);
        int persistence = configuration.persistencePeriods(siteCode);

        int snapshots = 0;
        int raised = 0;
        int cleared = 0;
        for (FacilityRoom room : facilities.findActiveRooms(siteCode)) {
            UtilisationSnapshot snapshot = snapshot(room, period, byRoom.get(room.id()),
                    planned.getOrDefault(room.id(), 0), availableMinutes, actor, channel, now);
            snapshots++;
            Outcome under = evaluate(snapshot, UtilisationSignal.Kind.UNDER_UTILISED,
                    UtilisationPolicy.underUtilised(snapshot, underThreshold), underThreshold,
                    "Utilisation " + pct(snapshot.utilisationRate()) + " is under the " + pct(underThreshold)
                            + " threshold for the period.", actor, channel, now);
            List<UtilisationSnapshot> recent = repository.findRecentSnapshots(room.id(), period.end(),
                    persistence);
            Outcome gap = evaluate(snapshot, UtilisationSignal.Kind.PLANNED_ACTUAL_GAP,
                    UtilisationPolicy.persistentGap(recent, persistence, gapThreshold), gapThreshold,
                    "Planned occupancy " + pct(snapshot.plannedOccupancyRate()) + " against observed utilisation "
                            + pct(snapshot.utilisationRate()) + " for " + persistence + " consecutive period(s).",
                    actor, channel, now);
            raised += (under == Outcome.RAISED ? 1 : 0) + (gap == Outcome.RAISED ? 1 : 0);
            cleared += (under == Outcome.CLEARED ? 1 : 0) + (gap == Outcome.CLEARED ? 1 : 0);
        }
        RunSummary summary = new RunSummary(siteCode, period.start(), period.end(), snapshots, raised, cleared,
                repository.findSignals(siteCode, true).size(), now);
        audit.record(actor, channel, AuditAction.UTILISATION_SNAPSHOT_RECORDED, "UtilisationRun",
                siteCode + ":" + period.end(), siteCode, null, summary);
        return summary;
    }

    @Transactional(readOnly = true)
    public List<UtilisationSignal> signals(String siteCode, boolean activeOnly, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel, "UtilisationSignal",
                "list");
        return authorization.filterBySite(actor, repository.findSignals(siteCode, activeOnly),
                UtilisationSignal::siteCode);
    }

    @Transactional(readOnly = true)
    public List<UtilisationSnapshot> latestSnapshots(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel,
                "UtilisationSnapshot", "list");
        return authorization.filterBySite(actor, repository.findLatestSnapshots(siteCode),
                UtilisationSnapshot::siteCode);
    }

    private UtilisationPolicy.Period periodFor(Instant requestedEnd, int periodDays, Instant now) {
        if (requestedEnd == null) {
            return UtilisationPolicy.latestCompletePeriod(now, periodDays);
        }
        if (!UtilisationPolicy.isPeriodBoundary(requestedEnd, periodDays)) {
            throw new FacilitiesException.ValidationFailedException("periodEnd must be a reporting-period boundary ("
                    + periodDays + "-day periods from Monday 2024-01-01T00:00Z).");
        }
        if (requestedEnd.isAfter(now)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Only a complete reporting period can be reconciled; that one has not ended.");
        }
        return UtilisationPolicy.periodEndingAt(requestedEnd, periodDays);
    }

    private UtilisationSnapshot snapshot(FacilityRoom room, UtilisationPolicy.Period period,
            BookingUtilisationPort.ObservedUtilisation observed, int plannedHeadcount, long availableMinutes,
            ActorContext actor, SourceChannel channel, Instant now) {
        int bookingCount = observed == null ? 0 : observed.bookingCount();
        int takenUp = observed == null ? 0 : observed.takenUpCount();
        long used = observed == null ? 0 : observed.usedMinutes();
        long attendees = observed == null ? 0 : observed.totalExpectedAttendees();
        UtilisationPolicy.Rates rates = UtilisationPolicy.rates(room.capacity(), takenUp, used, attendees,
                availableMinutes);
        UtilisationSnapshot fresh = new UtilisationSnapshot(UUID.randomUUID(), room.siteCode(), room.id(),
                room.roomCode(), period.start(), period.end(), room.capacity(), room.bookable(), bookingCount, takenUp,
                observed == null ? 0 : observed.noShowCount(), observed == null ? 0 : observed.bookedMinutes(), used,
                availableMinutes, rates.frequencyRate(), rates.occupancyRate(), rates.utilisationRate(),
                plannedHeadcount, UtilisationPolicy.plannedOccupancy(plannedHeadcount, room.capacity()), now,
                RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId()));
        return repository.saveSnapshot(repository.findSnapshot(room.id(), period.start(), period.end())
                .map(existing -> existing.refreshedFrom(fresh, actor.actorId(), now, channel, actor.correlationId()))
                .orElse(fresh));
    }

    private enum Outcome {
        RAISED,
        CLEARED,
        UNCHANGED
    }

    /**
     * Raises, refreshes or clears one kind of signal for one space.
     *
     * <p>An unobservable space (no capacity in S152, or not bookable and never booked) neither raises nor
     * clears: S159 has nothing to say about it, and silence is not a reason to change a signal either way.
     */
    private Outcome evaluate(UtilisationSnapshot snapshot, UtilisationSignal.Kind kind, boolean condition,
            BigDecimal threshold, String detail, ActorContext actor, SourceChannel channel, Instant now) {
        UtilisationSignal active = repository.findActiveSignal(snapshot.roomId(), kind).orElse(null);
        if (!snapshot.observable()) {
            return Outcome.UNCHANGED;
        }
        if (condition && active == null) {
            UtilisationSignal signal = repository.saveSignal(UtilisationSignal.raise(UUID.randomUUID(), snapshot, kind,
                    threshold, detail, actor.actorId(), now, channel, actor.correlationId()));
            audit.record(actor, channel, AuditAction.UTILISATION_SIGNAL_RAISED, "UtilisationSignal",
                    signal.id().toString(), signal.siteCode(), null, signal);
            publish(EVENT_SIGNAL_RAISED, signal, actor);
            return Outcome.RAISED;
        }
        if (condition) {
            repository.saveSignal(active.stillActive(snapshot, detail, actor.actorId(), now, channel,
                    actor.correlationId()));
            return Outcome.UNCHANGED;
        }
        if (active != null) {
            UtilisationSignal signal = repository.saveSignal(active.clear(snapshot, actor.actorId(), now, channel,
                    actor.correlationId()));
            audit.record(actor, channel, AuditAction.UTILISATION_SIGNAL_CLEARED, "UtilisationSignal",
                    signal.id().toString(), signal.siteCode(), active, signal);
            publish(EVENT_SIGNAL_CLEARED, signal, actor);
            return Outcome.CLEARED;
        }
        return Outcome.UNCHANGED;
    }

    private void publish(String eventType, UtilisationSignal signal, ActorContext actor) {
        outbox.record(eventType, 1, "UtilisationSignal", signal.id(), signal.siteCode(), actor.correlationId(),
                actor.actorId(), new SignalEvent(signal.id(), signal.siteCode(), signal.roomId(), signal.roomCode(),
                        signal.kind(), signal.status(), signal.latestPeriodEnd(), signal.latestUtilisationRate(),
                        signal.thresholdRate()));
    }

    private static String pct(BigDecimal rate) {
        return rate == null ? "n/a" : rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    public record RunSummary(String siteCode, Instant periodStart, Instant periodEnd, int roomsSnapshotted,
            int signalsRaised, int signalsCleared, int activeSignals, Instant ranAt) {
    }

    record SignalEvent(UUID signalId, String siteCode, UUID roomId, String roomCode, UtilisationSignal.Kind kind,
            UtilisationSignal.Status status, Instant periodEnd, BigDecimal utilisationRate, BigDecimal thresholdRate) {
    }
}
