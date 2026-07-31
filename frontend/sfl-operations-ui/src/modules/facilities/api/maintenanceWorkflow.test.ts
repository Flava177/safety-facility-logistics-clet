import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { FacilityFault, MaintenanceVendor, WorkOrder } from './dto';
import type { FaultPriority, WorkOrderStatus } from './enums';

/**
 * `permits` reads the actor's permissions, fetched once at module scope in the real application.
 * Mocked so each test states the actor it is describing rather than bootstrapping one.
 */
const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const {
  assignAction,
  cancelAction,
  canTransitionTo,
  closeAction,
  completeAction,
  dismissAction,
  faultBlockerSeverity,
  holdAction,
  raiseWorkOrderAction,
  reopenAction,
  startAction,
  triageAction,
} = await import('./workflow');

const order = (
  status: WorkOrderStatus,
  overrides: Partial<WorkOrder> = {},
): WorkOrder =>
  ({
    id: 'wo-1',
    workOrderNumber: 'WO-MAIN-000001',
    status,
    open: status !== 'CLOSED' && status !== 'CANCELLED',
    evidenceRequired: 0,
    ...overrides,
  }) as WorkOrder;

const fault = (overrides: Partial<FacilityFault> = {}): FacilityFault =>
  ({
    id: 'flt-1',
    faultNumber: 'FLT-MAIN-000001',
    status: 'REPORTED',
    open: true,
    priority: 'MEDIUM',
    workOrderId: null,
    triagedBy: null,
    ...overrides,
  }) as FacilityFault;

const vendor = (overrides: Partial<MaintenanceVendor> = {}): MaintenanceVendor =>
  ({
    id: 'v-1',
    vendorCode: 'ACME',
    name: 'Acme Facilities',
    assignable: true,
    unassignableReason: null,
    ...overrides,
  }) as MaintenanceVendor;

// =================================================================================================

describe('the closure evidence gate', () => {
  beforeEach(() => permits.mockReturnValue(true));

  /**
   * SRS-SFL-S153-02: "A workflow cannot be closed without required evidence or closure reason."
   * The count is the useful half of the message, and it is asserted because the service answers with
   * the same two numbers — two different sentences for one rule teaches a user to distrust both.
   */
  it('refuses closure below the required evidence, naming both counts', () => {
    const action = closeAction(order('IN_PROGRESS', { evidenceRequired: 2 }), 0);

    expect(action.allowed).toBe(false);
    expect(action.reason).toBe(
      'Required evidence must be attached before closure: 2 item(s) required, 0 attached.',
    );
  });

  it('permits closure once the shortfall is met', () => {
    expect(closeAction(order('IN_PROGRESS', { evidenceRequired: 2 }), 2).allowed).toBe(true);
    expect(closeAction(order('IN_PROGRESS', { evidenceRequired: 2 }), 3).allowed).toBe(true);
  });

  it('needs none when the order required none', () => {
    expect(closeAction(order('ASSIGNED', { evidenceRequired: 0 }), 0).allowed).toBe(true);
  });

  it('refuses closure without the closing permission, whatever the evidence', () => {
    permits.mockReturnValue(false);
    expect(closeAction(order('COMPLETED', { evidenceRequired: 0 }), 5).allowed).toBe(false);
  });

  it('refuses closure of an order that is already closed', () => {
    expect(closeAction(order('CLOSED'), 0).allowed).toBe(false);
  });
});

// =================================================================================================

describe('the work order state machine', () => {
  beforeEach(() => permits.mockReturnValue(true));

  /**
   * Transcribed from `WorkOrderStatus` in the service. Asserted rather than trusted because the
   * screen renders its buttons from this table, and a table that drifts offers moves the service
   * refuses.
   */
  it('allows reassignment: ASSIGNED to ASSIGNED is a legal move', () => {
    expect(canTransitionTo('ASSIGNED', 'ASSIGNED')).toBe(true);
  });

  it('reaches closure from any working state, not only from COMPLETED', () => {
    expect(canTransitionTo('ASSIGNED', 'CLOSED')).toBe(true);
    expect(canTransitionTo('IN_PROGRESS', 'CLOSED')).toBe(true);
    expect(canTransitionTo('ON_HOLD', 'CLOSED')).toBe(true);
    expect(canTransitionTo('COMPLETED', 'CLOSED')).toBe(true);
  });

  it('refuses to close an order nobody has been assigned', () => {
    // The route from OPEN is assignment or cancellation. Closing unassigned work would leave no
    // record of who did it.
    expect(canTransitionTo('OPEN', 'CLOSED')).toBe(false);
  });

  it('treats CLOSED and CANCELLED as terminal', () => {
    expect(canTransitionTo('CLOSED', 'IN_PROGRESS')).toBe(false);
    expect(canTransitionTo('CANCELLED', 'ASSIGNED')).toBe(false);
  });

  it('offers only the moves that are legal from here', () => {
    expect(startAction(order('OPEN')).allowed).toBe(false);
    expect(startAction(order('ASSIGNED')).allowed).toBe(true);
    expect(holdAction(order('OPEN')).allowed).toBe(false);
    expect(holdAction(order('IN_PROGRESS')).allowed).toBe(true);
    expect(completeAction(order('ON_HOLD')).allowed).toBe(false);
    expect(cancelAction(order('OPEN')).allowed).toBe(true);
  });

  it('resumes held work rather than treating a hold as a dead end', () => {
    // ON_HOLD -> IN_PROGRESS is legal, and it should be: picking a held job back up is starting it.
    expect(startAction(order('ON_HOLD')).allowed).toBe(true);
  });

  it('names the state in the refusal rather than only saying no', () => {
    expect(startAction(order('CLOSED')).reason).toBe('A closed work order cannot be started.');
  });
});

// =================================================================================================

describe('who may reopen completed work', () => {
  /**
   * SRS-SFL-S153-02: "Only authorised roles may approve, override, cancel or reopen workflow items."
   * A technician holds `_UPDATE` and not `_CLOSE`, which is the whole reason the two states are
   * separate — this is the guard that stops them undoing their own supervisor.
   */
  it('refuses a technician holding only the update permission', () => {
    permits.mockImplementation((permission) => permission === 'FACILITIES_WORK_ORDER_UPDATE');

    const action = reopenAction(order('COMPLETED'));

    expect(action.allowed).toBe(false);
    expect(action.reason).toContain('closing permission');
  });

  it('permits a supervisor holding the closing permission', () => {
    permits.mockReturnValue(true);
    expect(reopenAction(order('COMPLETED')).allowed).toBe(true);
  });

  it('refuses reopening anything that was never completed', () => {
    permits.mockReturnValue(true);
    expect(reopenAction(order('IN_PROGRESS')).allowed).toBe(false);
  });
});

// =================================================================================================

describe('assigning to a vendor', () => {
  beforeEach(() => permits.mockReturnValue(true));

  /**
   * `assignable` and `unassignableReason` are computed by the service and carried on every vendor.
   * Using its answer rather than comparing contract dates here is what keeps the two in step when a
   * contract expires mid-session.
   */
  it('refuses a vendor the service says cannot take work, with its reason', () => {
    const expired = vendor({
      assignable: false,
      unassignableReason: 'The contract for vendor ACME expired on 2026-07-30.',
    });

    const action = assignAction(expired);

    expect(action.allowed).toBe(false);
    expect(action.reason).toBe('The contract for vendor ACME expired on 2026-07-30.');
  });

  it('permits an assignable vendor, and in-house work with no vendor at all', () => {
    expect(assignAction(vendor()).allowed).toBe(true);
    expect(assignAction(null).allowed).toBe(true);
    expect(assignAction(undefined).allowed).toBe(true);
  });

  it('refuses without the assign permission', () => {
    permits.mockReturnValue(false);
    expect(assignAction(vendor()).allowed).toBe(false);
  });
});

// =================================================================================================

describe('what a fault does to its space', () => {
  /**
   * Mirrors `FaultReadinessPolicy.severityFor`. Asserted because the report and triage dialogs show
   * this to somebody *before* they commit, and a preview that disagrees with what the service then
   * does is worse than no preview.
   */
  it('blocks the space when a critical fault is reported', () => {
    expect(faultBlockerSeverity('CRITICAL')).toBe('CRITICAL');
  });

  it('degrades rather than blocks at high priority', () => {
    expect(faultBlockerSeverity('HIGH')).toBe('MAJOR');
  });

  it('raises nothing below the site threshold', () => {
    // A flickering light does not take an examination hall out of service.
    expect(faultBlockerSeverity('MEDIUM')).toBeNull();
    expect(faultBlockerSeverity('LOW')).toBeNull();
  });

  it('honours a site that has lowered its threshold', () => {
    expect(faultBlockerSeverity('MEDIUM', 'LOW')).toBe('MINOR');
    expect(faultBlockerSeverity('LOW', 'LOW')).toBe('ADVISORY');
  });

  it('honours a site that has raised it', () => {
    expect(faultBlockerSeverity('HIGH', 'CRITICAL')).toBeNull();
    expect(faultBlockerSeverity('CRITICAL', 'CRITICAL')).toBe('CRITICAL');
  });
});

// =================================================================================================

describe('the fault workflow', () => {
  beforeEach(() => permits.mockReturnValue(true));

  it('triages only an untriaged fault, and says who did it when refusing', () => {
    expect(triageAction(fault()).allowed).toBe(true);

    const action = triageAction(fault({ status: 'TRIAGED', triagedBy: 'supervisor' }));
    expect(action.allowed).toBe(false);
    expect(action.reason).toContain('supervisor');
  });

  it('raises a work order once, and points at the existing one afterwards', () => {
    expect(raiseWorkOrderAction(fault()).allowed).toBe(true);

    const action = raiseWorkOrderAction(fault({ workOrderId: 'wo-1' }));
    expect(action.allowed).toBe(false);
    expect(action.reason).toBe('This fault already has a work order.');
  });

  it('refuses to raise work against a fault that is already closed', () => {
    expect(raiseWorkOrderAction(fault({ status: 'RESOLVED', open: false })).allowed).toBe(false);
  });

  it('dismisses only an open fault', () => {
    expect(dismissAction(fault()).allowed).toBe(true);
    expect(dismissAction(fault({ status: 'REJECTED', open: false })).allowed).toBe(false);
  });

  it('refuses triage and dismissal without the triage permission', () => {
    permits.mockReturnValue(false);
    expect(triageAction(fault()).allowed).toBe(false);
    expect(dismissAction(fault()).allowed).toBe(false);
  });
});

// =================================================================================================

describe('the priority ordering the thresholds depend on', () => {
  /**
   * Two configurable rules — the blocking threshold and the closure-evidence threshold — are both
   * expressed as "at least this priority", so the order of the array is behaviour rather than
   * presentation. Reordering it silently changes both.
   */
  it('runs from low to critical', () => {
    const ascending: FaultPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    ascending.forEach((priority, index) => {
      const above = ascending.slice(index);
      above.forEach((higher) => {
        expect(faultBlockerSeverity(higher, priority)).not.toBeNull();
      });
    });
  });
});
