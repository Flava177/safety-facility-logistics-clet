package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.booking.domain.policy.ReadinessHoldPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two sweeps that keep the diary honest — SRS-SFL-S159-02, -03.
 *
 * <h2>Why readiness reaches bookings by a sweep rather than by a port</h2>
 *
 * S153 tells readiness about a fault through {@code ExternalBlockerPort}, synchronously, in the same
 * transaction. The obvious symmetry would be a port readiness calls when a space changes, so a hall
 * blocked at 09:00 flags its bookings at 09:00.
 *
 * <p>It is the wrong shape here, and the reason is the dependency arrow. Booking depends on the
 * estate and on readiness — it reads a space to decide whether it can be used. A port pointing back
 * would make readiness depend on bookings, which is the same inversion the S152 architecture test
 * exists to prevent, and it would mean assessing a space could fail because a booking three weeks out
 * was in a state nobody assessing the hall was thinking about.
 *
 * <p>So this runs on a timer instead, and the cost is latency rather than correctness: a hall blocked
 * at 09:00 has its bookings flagged by 09:15. Both sweeps are idempotent — {@code withReadinessHold}
 * returns the same instance when the reason has not changed, and a booking already {@code NO_SHOW} is
 * not a candidate — so running twice, or on two instances at once, changes nothing.
 *
 * <h2>Why the no-show grace is not in the query</h2>
 *
 * It is site-scoped runtime configuration, and a query cannot carry a different threshold per row.
 * {@link #sweepNoShows} therefore asks for every confirmed booking whose start has passed unstarted —
 * a small set, since it drains as fast as it fills — and applies each site's own grace in memory.
 */
@Service
public class BookingReconciliationService {

    /** What one readiness pass changed. */
    public record ReadinessSweep(int holdsPlaced, int holdsCleared, int examined, Instant evaluatedAt) {
        public int total() {
            return holdsPlaced + holdsCleared;
        }
    }

    /** What one no-show pass changed. */
    public record NoShowSweep(int recorded, int examined, Instant evaluatedAt) {
    }

    private final BookingRepository bookings;
    private final FacilitiesRepository facilities;
    private final BookingApplicationService booking;
    private final BookingConfiguration configuration;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public BookingReconciliationService(BookingRepository bookings, FacilitiesRepository facilities,
            BookingApplicationService booking, BookingConfiguration configuration, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.bookings = bookings;
        this.facilities = facilities;
        this.booking = booking;
        this.configuration = configuration;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Places and clears readiness holds across every live booking from now forward.
     *
     * <p>Clearing matters as much as placing. A hall repaired on Wednesday must release Friday's
     * examination without anybody remembering to, or the flag becomes noise people learn to ignore —
     * which is worse than not having flagged it at all.
     */
    @Transactional
    public ReadinessSweep sweepReadinessHolds(ActorContext actor) {
        Instant at = clock.instant();
        List<Booking> live = bookings.findUpcoming(at, configuration.sweepBatchSize(null));
        Map<UUID, Optional<FacilityRoom>> rooms = new HashMap<>();

        int placed = 0;
        int cleared = 0;
        for (Booking candidate : live) {
            Optional<FacilityRoom> room = rooms.computeIfAbsent(candidate.roomId(), facilities::findRoom);
            if (room.isEmpty()) {
                continue;
            }
            ReadinessHoldReason reason = holdFor(candidate, room.get());
            if (reason == candidate.readinessHoldReason()) {
                continue;
            }
            Booking updated = bookings.saveBooking(candidate.withReadinessHold(reason, at));
            if (reason == null) {
                cleared++;
                audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BOOKING_READINESS_HOLD_CLEARED,
                        "Booking", updated.id().toString(), updated.siteCode(), candidate, updated);
                publish("ifimp.booking.readiness-hold-cleared", updated, actor);
            } else {
                placed++;
                audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BOOKING_READINESS_HOLD_PLACED,
                        "Booking", updated.id().toString(), updated.siteCode(), candidate, updated);
                publish("ifimp.booking.readiness-hold-placed", updated, actor);
            }
        }
        return new ReadinessSweep(placed, cleared, live.size(), at);
    }

    /** Re-derives the holds on one space. Called after something is known to have changed about it. */
    @Transactional
    public ReadinessSweep reconcileRoom(UUID roomId, ActorContext actor) {
        Instant at = clock.instant();
        Optional<FacilityRoom> room = facilities.findRoom(roomId);
        if (room.isEmpty()) {
            return new ReadinessSweep(0, 0, 0, at);
        }
        List<Booking> live = bookings.findUpcomingForRoom(roomId, at, configuration.sweepBatchSize(null));
        int placed = 0;
        int cleared = 0;
        for (Booking candidate : live) {
            ReadinessHoldReason reason = holdFor(candidate, room.get());
            if (reason == candidate.readinessHoldReason()) {
                continue;
            }
            Booking updated = bookings.saveBooking(candidate.withReadinessHold(reason, at));
            AuditAction action = reason == null ? AuditAction.BOOKING_READINESS_HOLD_CLEARED
                    : AuditAction.BOOKING_READINESS_HOLD_PLACED;
            audit.record(actor, SourceChannel.SCHEDULER, action, "Booking", updated.id().toString(),
                    updated.siteCode(), candidate, updated);
            if (reason == null) {
                cleared++;
            } else {
                placed++;
            }
        }
        return new ReadinessSweep(placed, cleared, live.size(), at);
    }

    /**
     * Marks confirmed bookings nobody took up, and releases what they were holding.
     *
     * <p>The {@link NoShowRecord} is written in the same transaction as the status change. A status
     * without its record would leave the "how much room-time did we lose?" question answerable only by
     * re-deriving it from bookings, which is the thing the record exists to avoid.
     */
    @Transactional
    public NoShowSweep sweepNoShows(ActorContext actor) {
        Instant at = clock.instant();
        List<Booking> candidates = bookings.findNoShowCandidates(at, configuration.sweepBatchSize(null));

        int recorded = 0;
        for (Booking candidate : candidates) {
            if (!candidate.isNoShowAt(at, configuration.noShowGrace(candidate.siteCode()))) {
                continue;
            }
            Booking marked = bookings.saveBooking(candidate.markNoShow(actor.actorId(), at,
                    SourceChannel.SCHEDULER, actor.correlationId()));
            NoShowRecord record = bookings.saveNoShow(NoShowRecord.from(marked, at));
            booking.releaseAllocations(marked, actor, SourceChannel.SCHEDULER);
            booking.skipSetupTasks(marked, "Booking recorded as a no-show.", actor, at,
                    SourceChannel.SCHEDULER);

            audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BOOKING_NO_SHOW_RECORDED, "Booking",
                    marked.id().toString(), marked.siteCode(), candidate, record);
            publish("ifimp.booking.no-show", marked, actor);
            recorded++;
        }
        return new NoShowSweep(recorded, candidates.size(), at);
    }

    private ReadinessHoldReason holdFor(Booking candidate, FacilityRoom room) {
        return ReadinessHoldPolicy.holdFor(candidate.purpose(), room.readinessStatus(), room.bookable(),
                room.examinationCapable(), room.lifecycleStatus().isOperational(), room.readinessLocked());
    }

    private void publish(String eventType, Booking subject, ActorContext actor) {
        outbox.record(eventType, 1, "Booking", subject.id(), subject.siteCode(), actor.correlationId(),
                actor.actorId(), subject);
    }
}
