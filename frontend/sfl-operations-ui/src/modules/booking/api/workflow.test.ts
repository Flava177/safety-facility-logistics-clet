import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Booking, SetupTask } from './dto';
import type { BookingStatus } from './enums';

/**
 * The S159 control rules.
 *
 * Two things here are worth testing and nothing else is: **which permission gates which control**,
 * and **when a control is hidden rather than disabled**. Both are transcriptions of decisions the
 * service makes, and both have already been got wrong once in this codebase — S153 shipped a Close
 * button permanently disabled with "you do not have permission", which is the failure the
 * hidden/disabled split exists to prevent.
 *
 * The rule that most needs a test is the misnamed one. `FACILITIES_BOOKING_CANCEL` reads as "may
 * cancel"; `BookingApplicationService.requireMayAct` uses it as the "may act on somebody else's
 * booking" grant and routes cancel, reschedule, start and complete through it identically. Gating
 * reschedule on `FACILITIES_BOOKING_REQUEST` — the reading the names invite — offers a requester the
 * control on a hall booked by the registry, and the service then refuses it.
 */

const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

/** The actor id the client sends as `X-SFL-User`, which is what "your own booking" compares against. */
vi.mock('shared/api/config', () => ({ sflActor: { user: 'ama.mensah' } }));

const {
  canCancel,
  canComplete,
  canDecide,
  canOverrideReadiness,
  canRequest,
  canReschedule,
  canResolveSetupTask,
  canStart,
  isOwnBooking,
  isTerminal,
} = await import('./workflow');

const booking = (
  status: BookingStatus,
  overrides: Partial<Booking> = {},
): Booking =>
  ({
    id: 'booking-1',
    bookingReference: 'BKG-0001',
    siteCode: 'ACCRA',
    status,
    requestedBy: 'kofi.owusu',
    approvalRequired: true,
    approvalId: null,
    readinessHoldReason: null,
    metadata: { version: 3 },
    ...overrides,
  }) as Booking;

const own = (status: BookingStatus, overrides: Partial<Booking> = {}) =>
  booking(status, { requestedBy: 'ama.mensah', ...overrides });

const task = (status: SetupTask['status']): SetupTask =>
  ({ id: 'task-1', status, overdue: false }) as SetupTask;

/** Grants exactly the listed permissions and refuses everything else. */
const holding = (...granted: string[]) => {
  permits.mockImplementation((permission) => permission === undefined || granted.includes(permission));
};

beforeEach(() => {
  permits.mockReset();
});

describe('S159 booking controls', () => {
  describe('acting on somebody else’s booking', () => {
    it('hides move, start, complete and cancel from a requester on a booking that is not theirs', () => {
      // A requester holds BOOKING_REQUEST and deliberately not BOOKING_CANCEL. Every one of these
      // would be refused by `requireMayAct`, so none of them is offered.
      holding('FACILITIES_BOOKING_READ', 'FACILITIES_BOOKING_REQUEST');
      const theirs = booking('CONFIRMED');

      expect(canReschedule(theirs).kind).toBe('hidden');
      expect(canStart(theirs).kind).toBe('hidden');
      expect(canComplete(theirs).kind).toBe('hidden');
      expect(canCancel(theirs).kind).toBe('hidden');
    });

    it('offers all four on the requester’s own booking with no extra permission', () => {
      // The half the permission names get wrong: your own booking needs nothing beyond requesting it.
      holding('FACILITIES_BOOKING_READ', 'FACILITIES_BOOKING_REQUEST');

      expect(canReschedule(own('CONFIRMED')).kind).toBe('allowed');
      expect(canStart(own('CONFIRMED')).kind).toBe('allowed');
      expect(canComplete(own('IN_USE')).kind).toBe('allowed');
      expect(canCancel(own('CONFIRMED')).kind).toBe('allowed');
    });

    it('offers all four on anybody’s booking to a holder of BOOKING_CANCEL', () => {
      holding('FACILITIES_BOOKING_CANCEL');
      const theirs = booking('CONFIRMED');

      expect(canReschedule(theirs).kind).toBe('allowed');
      expect(canStart(theirs).kind).toBe('allowed');
      expect(canCancel(theirs).kind).toBe('allowed');
    });
  });

  describe('state, which disables rather than hides', () => {
    it('disables a move on a booking in use and says what to do instead', () => {
      holding('FACILITIES_BOOKING_CANCEL');
      const state = canReschedule(booking('IN_USE'));

      expect(state.kind).toBe('disabled');
      expect(state.kind === 'disabled' && state.reason).toContain('Complete it');
    });

    it('disables every transition on a terminal booking', () => {
      holding('FACILITIES_BOOKING_CANCEL');

      (['COMPLETED', 'REJECTED', 'CANCELLED', 'NO_SHOW'] as BookingStatus[]).forEach((status) => {
        expect(canReschedule(booking(status)).kind, status).toBe('disabled');
        expect(canCancel(booking(status)).kind, status).toBe('disabled');
        expect(isTerminal(status), status).toBe(true);
      });
    });

    it('starts only a confirmed booking', () => {
      holding('FACILITIES_BOOKING_CANCEL');

      expect(canStart(booking('CONFIRMED')).kind).toBe('allowed');
      expect(canStart(booking('REQUESTED')).kind).toBe('disabled');
      expect(canStart(booking('IN_USE')).kind).toBe('disabled');
    });

    it('completes only a booking that has started', () => {
      holding('FACILITIES_BOOKING_CANCEL');

      expect(canComplete(booking('IN_USE')).kind).toBe('allowed');
      expect(canComplete(booking('CONFIRMED')).kind).toBe('disabled');
    });
  });

  describe('deciding', () => {
    it('is hidden without BOOKING_APPROVE', () => {
      holding('FACILITIES_BOOKING_READ', 'FACILITIES_BOOKING_CANCEL');
      expect(canDecide(booking('REQUESTED')).kind).toBe('hidden');
    });

    it('is offered on a requested booking to an approver', () => {
      holding('FACILITIES_BOOKING_APPROVE');
      expect(canDecide(booking('REQUESTED')).kind).toBe('allowed');
    });

    it('refuses self-approval, and disables rather than hides so the rule is legible', () => {
      /*
        Separation of duties, and administrators are not exempt — `BookingApplicationService.decide`
        refuses it outright. Disabled, not hidden: the actor plainly holds the authority, and what
        they lack is distance from this particular request. Hiding it would read as a missing
        permission and send them to look for one.
      */
      holding('FACILITIES_BOOKING_APPROVE');
      const state = canDecide(own('REQUESTED'));

      expect(state.kind).toBe('disabled');
      expect(state.kind === 'disabled' && state.reason).toContain('your own');
    });

    it('disables a decision on a booking that has already been decided', () => {
      holding('FACILITIES_BOOKING_APPROVE');
      expect(canDecide(booking('CONFIRMED')).kind).toBe('disabled');
    });
  });

  describe('the readiness override', () => {
    it('is not implied by holding every other booking permission', () => {
      /*
        `IFIMP_MAINTENANCE_SUPERVISOR` holds `FACILITIES_READINESS_OVERRIDE` and deliberately not
        `FACILITIES_BOOKING_OVERRIDE`. The matrix says why: a supervisor who needs a blocked hall used
        should clear or downgrade the blocker, which leaves a readiness record somebody can review,
        rather than book past it and leave the hall reading BLOCKED to everybody else.
      */
      holding(
        'FACILITIES_BOOKING_READ',
        'FACILITIES_BOOKING_REQUEST',
        'FACILITIES_BOOKING_CANCEL',
        'FACILITIES_READINESS_OVERRIDE',
      );
      expect(canOverrideReadiness().kind).toBe('hidden');
    });

    it('is offered to a holder of BOOKING_OVERRIDE', () => {
      holding('FACILITIES_BOOKING_OVERRIDE');
      expect(canOverrideReadiness().kind).toBe('allowed');
    });
  });

  describe('turnaround tasks', () => {
    it('is hidden without SETUP_TASK_MANAGE, so a reader sees the queue and no controls', () => {
      // The queue itself is gated on BOOKING_READ, not on this. A technician holds this and no
      // booking-request permission; the reverse is true of a requester.
      holding('FACILITIES_BOOKING_READ');
      expect(canResolveSetupTask(task('PENDING')).kind).toBe('hidden');
    });

    it('resolves a pending task and disables an already-resolved one', () => {
      holding('FACILITIES_SETUP_TASK_MANAGE');

      expect(canResolveSetupTask(task('PENDING')).kind).toBe('allowed');
      expect(canResolveSetupTask(task('DONE')).kind).toBe('disabled');
      expect(canResolveSetupTask(task('SKIPPED')).kind).toBe('disabled');
    });
  });

  describe('ownership', () => {
    it('compares actor ids without regard to case', () => {
      // Actor ids arrive from a header and from a JWT subject, and the two have differed in case.
      expect(isOwnBooking(booking('CONFIRMED', { requestedBy: 'AMA.MENSAH' }))).toBe(true);
      expect(isOwnBooking(booking('CONFIRMED', { requestedBy: 'kofi.owusu' }))).toBe(false);
    });
  });

  describe('requesting', () => {
    it('is hidden from an actor who can only read the diary', () => {
      holding('FACILITIES_BOOKING_READ');
      expect(canRequest().kind).toBe('hidden');
    });
  });
});
