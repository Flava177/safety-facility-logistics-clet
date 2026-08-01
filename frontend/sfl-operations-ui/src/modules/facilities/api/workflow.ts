import { permits } from 'shared/layout/actorPermissions';
import type {
  FacilityAsset,
  FacilityFault,
  MaintenanceVendor,
  ReadinessBlocker,
  Site,
  Space,
  WorkOrder,
} from './dto';
import type {
  BlockerSeverity,
  FaultPriority,
  LocationReadinessStatus,
  WorkOrderStatus,
} from './enums';
import { faultPriorities } from './enums';

/**
 * What an actor may do next, and what the estate's own state allows.
 *
 * Two different questions, answered together because a screen has to combine them before it renders
 * a button:
 *
 * - **May this actor?** — `permits`, backed by `GET /actor/permissions`, which is the service's own
 *   answer rather than a guess from role names.
 * - **May this record?** — the same rules the service enforces, evaluated locally so a control that
 *   would be refused is disabled with a reason instead of failing on click.
 *
 * This is a usability layer and never the enforcement point: every one of these calls is authorised
 * again server-side, and the service refuses regardless of what the screen offered.
 */

/** A control the screen may render, with why it is unavailable when it is. */
export interface Action {
  allowed: boolean;
  /** Present when `allowed` is false — shown as the disabled control's tooltip. */
  reason?: string;
}

const allow: Action = { allowed: true };
const deny = (reason: string): Action => ({ allowed: false, reason });

const NO_PERMISSION = 'You do not have permission for this action.';

// ---- sites ------------------------------------------------------------------------------------

export const canManageSites = (): boolean => permits('FACILITIES_SITE_MANAGE');

/**
 * Whether this actor may declare or stand down examination mode.
 *
 * Deliberately not held by `FACILITIES_MANAGER`: declaring an examination is a centre-level
 * operational decision, so the permission sits with the centre manager, command and the director.
 */
export const changeOperatingModeAction = (site: Site): Action => {
  if (!permits('FACILITIES_OPERATING_MODE_CHANGE')) {
    return deny(NO_PERMISSION);
  }
  if (site.lifecycleStatus !== 'ACTIVE') {
    return deny(`A ${site.lifecycleStatus.toLowerCase()} site cannot change operating mode.`);
  }
  return allow;
};

// ---- spaces -----------------------------------------------------------------------------------

/**
 * Whether a space's attributes may be edited.
 *
 * The readiness lock is the interesting case: while it is engaged the service refuses the edit, and
 * the way through is to release the lock — an audited act — rather than to edit around it.
 */
export const updateSpaceAction = (space: Space): Action => {
  if (!permits('FACILITIES_SPACE_MANAGE')) {
    return deny(NO_PERMISSION);
  }
  if (space.readinessLocked) {
    return deny(
      `Locked for examination use by ${space.readinessLockedBy ?? 'an officer'}. Release the lock first.`,
    );
  }
  if (space.lifecycleStatus === 'ARCHIVED') {
    return deny('Archived spaces cannot be edited.');
  }
  return allow;
};

/**
 * Whether a readiness status may be set by hand, for a given target.
 *
 * The one rule the whole module turns on: READY is refused while a critical blocker is open. Checked
 * here so the option is disabled with the count rather than offered and then refused.
 */
export const setReadinessAction = (
  target: LocationReadinessStatus,
  openBlockers: ReadinessBlocker[],
): Action => {
  if (!permits('FACILITIES_READINESS_ASSESS')) {
    return deny(NO_PERMISSION);
  }
  if (target !== 'READY') {
    return allow;
  }
  const critical = openBlockers.filter(
    (blocker) => !blocker.resolved && blocker.severity === 'CRITICAL',
  ).length;
  return critical > 0
    ? deny(`${critical} critical blocker${critical === 1 ? '' : 's'} must be resolved first.`)
    : allow;
};

export const lockAction = (space: Space): Action => {
  if (!permits('FACILITIES_READINESS_OVERRIDE')) {
    return deny(NO_PERMISSION);
  }
  return space.readinessLocked ? deny('This space is already locked.') : allow;
};

export const unlockAction = (space: Space): Action => {
  if (!permits('FACILITIES_READINESS_OVERRIDE')) {
    return deny(NO_PERMISSION);
  }
  return space.readinessLocked ? allow : deny('This space is not locked.');
};

// ---- assets -----------------------------------------------------------------------------------

export const changeAssetStatusAction = (asset: FacilityAsset): Action => {
  if (!permits('FACILITIES_ASSET_MANAGE')) {
    return deny(NO_PERMISSION);
  }
  if (asset.lifecycleStatus === 'ARCHIVED') {
    return deny('Archived assets cannot change status.');
  }
  return allow;
};

/**
 * The readiness consequence of putting an asset into a state.
 *
 * Shown before the change is confirmed, because "this generator is out of service" and "Examination
 * Hall A is now blocked" are the same fact and an operator should see both before committing to it.
 * Mirrors `ReadinessApplicationService.severityFor`.
 */
export const assetBlockerSeverity = (
  asset: FacilityAsset,
  status: FacilityAsset['operationalStatus'],
): BlockerSeverity | null => {
  const impairs =
    status === 'DEGRADED' || status === 'UNDER_MAINTENANCE' || status === 'OUT_OF_SERVICE';
  if (!impairs) {
    return null;
  }
  const total = status === 'OUT_OF_SERVICE';
  switch (asset.criticality) {
    case 'CRITICAL':
      return total ? 'CRITICAL' : 'MAJOR';
    case 'HIGH':
      return total ? 'MAJOR' : 'MINOR';
    case 'MEDIUM':
      return 'MINOR';
    default:
      return 'ADVISORY';
  }
};

// ---- readiness --------------------------------------------------------------------------------

export const canAssessReadiness = (): boolean => permits('FACILITIES_READINESS_ASSESS');
export const canManageChecklists = (): boolean => permits('FACILITIES_READINESS_CHECKLIST_MANAGE');
export const canManageAssets = (): boolean => permits('FACILITIES_ASSET_MANAGE');
/**
 * Adding to the estate hierarchy — a **building**, a **floor** or a space.
 *
 * All three are `FACILITIES_SPACE_MANAGE` in `FacilitiesMasterDataService`, and that is not an
 * oversight to route around here: a building and a floor exist only to hold spaces, and somebody
 * trusted to place a moot courtroom is trusted to say which floor it is on. `canManageSites` is the
 * separate, higher-water grant, because creating a *site* is creating a centre.
 */
export const canManageSpaces = (): boolean => permits('FACILITIES_SPACE_MANAGE');
export const canManageZones = (): boolean => permits('FACILITIES_ZONE_MANAGE');
export const canRegisterDevices = (): boolean => permits('FACILITIES_DEVICE_REFERENCE_REGISTER');
export const canDrillDown = (): boolean => permits('FACILITIES_DASHBOARD_DRILLDOWN');
export const canVerifyAuditChain = (): boolean => permits('FACILITIES_AUDIT_INTEGRITY_CHECK');
export const canManageConfiguration = (): boolean => permits('FACILITIES_CONFIG_MANAGE');

// =================================================================================================
// S153 CMMS
//
// Every guard below mirrors a rule the service enforces. None of them replaces it: the service
// refuses regardless, and these exist so an operator is told *before* they fill in a form rather
// than after. Where a rule has a count in it, the count is the useful part and is carried through.
// =================================================================================================

/**
 * Which work-order transitions are legal from the current status.
 *
 * Transcribed from `WorkOrderStatus`'s own table in the service, not from a remembered sequence.
 * Two entries surprise people and both are deliberate: `ASSIGNED → ASSIGNED` is legal, because
 * reassignment is a change of owner rather than a state; and `CLOSED` is reachable from any working
 * state, because the gate on closing is the closing permission and the evidence rule, not the route
 * taken to get there.
 */
const TRANSITIONS: Record<WorkOrderStatus, WorkOrderStatus[]> = {
  OPEN: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CLOSED', 'CANCELLED'],
  IN_PROGRESS: ['ASSIGNED', 'ON_HOLD', 'COMPLETED', 'CLOSED', 'CANCELLED'],
  ON_HOLD: ['ASSIGNED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED'],
  COMPLETED: ['CLOSED', 'IN_PROGRESS'],
  CLOSED: [],
  CANCELLED: [],
};

/** Whether the service's state machine has this move at all. */
export const canTransitionTo = (from: WorkOrderStatus, to: WorkOrderStatus): boolean =>
  TRANSITIONS[from].includes(to);

/**
 * Whether a work order can be closed, and what is missing when it cannot.
 *
 * SRS-SFL-S153-02: "A workflow cannot be closed without required evidence or closure reason." The
 * evidence half is checked here so the button carries the shortfall as a number; the reason half is
 * the form's own required field. The service answers `CLOSURE_EVIDENCE_MISSING` naming both counts,
 * and this wording matches it deliberately — two different sentences for one rule is how a user
 * learns to distrust both.
 */
export const closeAction = (order: WorkOrder, attachedEvidence: number): Action => {
  if (!permits('FACILITIES_WORK_ORDER_CLOSE')) {
    return deny(NO_PERMISSION);
  }
  if (!canTransitionTo(order.status, 'CLOSED')) {
    return deny(`A ${humanStatus(order.status)} work order cannot be closed.`);
  }
  if (attachedEvidence < order.evidenceRequired) {
    return deny(
      `Required evidence must be attached before closure: ${order.evidenceRequired} item(s) required, ${attachedEvidence} attached.`,
    );
  }
  return allow;
};

/**
 * Reopening a completed order.
 *
 * Takes the closing permission rather than the updating one, because it reverses somebody's
 * judgement that the work was finished. SRS-SFL-S153-02: "Only authorised roles may approve,
 * override, cancel or reopen workflow items." A technician holds `_UPDATE` and not `_CLOSE`, so this
 * is the guard that stops them undoing their own supervisor.
 */
export const reopenAction = (order: WorkOrder): Action => {
  if (!permits('FACILITIES_WORK_ORDER_CLOSE')) {
    return deny('Reopening a completed work order requires the closing permission.');
  }
  return canTransitionTo(order.status, 'IN_PROGRESS') && order.status === 'COMPLETED'
    ? allow
    : deny('Only a completed work order can be reopened.');
};

export const startAction = (order: WorkOrder): Action => transition(order, 'IN_PROGRESS', 'started');
export const holdAction = (order: WorkOrder): Action => transition(order, 'ON_HOLD', 'put on hold');
export const completeAction = (order: WorkOrder): Action =>
  transition(order, 'COMPLETED', 'completed');

export const cancelAction = (order: WorkOrder): Action => {
  if (!permits('FACILITIES_WORK_ORDER_CANCEL')) {
    return deny(NO_PERMISSION);
  }
  return canTransitionTo(order.status, 'CANCELLED')
    ? allow
    : deny(`A ${humanStatus(order.status)} work order cannot be cancelled.`);
};

/**
 * Whether work may be assigned to a vendor today.
 *
 * The service refuses an expired contract with `VALIDATION_FAILED`, and it returns `assignable` and
 * `unassignableReason` on every vendor so this side does not have to compare dates. Using its answer
 * rather than recomputing is what keeps the two in step when the contract expires mid-session.
 */
export const assignAction = (vendor?: MaintenanceVendor | null): Action => {
  if (!permits('FACILITIES_WORK_ORDER_ASSIGN')) {
    return deny(NO_PERMISSION);
  }
  if (vendor && !vendor.assignable) {
    return deny(vendor.unassignableReason ?? `Vendor ${vendor.vendorCode} cannot take work.`);
  }
  return allow;
};

/** Triage confirms the priority and starts the clock. Only an untriaged fault can be triaged. */
export const triageAction = (fault: FacilityFault): Action => {
  if (!permits('FACILITIES_FAULT_TRIAGE')) {
    return deny(NO_PERMISSION);
  }
  return fault.status === 'REPORTED'
    ? allow
    : deny(`This fault was triaged ${fault.triagedBy ? `by ${fault.triagedBy}` : 'already'}.`);
};

/** Rejection, duplication and withdrawal. Terminal, so only an open fault may be dismissed. */
export const dismissAction = (fault: FacilityFault): Action => {
  if (!permits('FACILITIES_FAULT_TRIAGE')) {
    return deny(NO_PERMISSION);
  }
  return fault.open ? allow : deny('This fault is already closed.');
};

/** A work order answers a fault. One fault, one work order. */
export const raiseWorkOrderAction = (fault: FacilityFault): Action => {
  if (!permits('FACILITIES_WORK_ORDER_CREATE')) {
    return deny(NO_PERMISSION);
  }
  if (fault.workOrderId) {
    return deny('This fault already has a work order.');
  }
  return fault.open ? allow : deny('A closed fault cannot have work raised against it.');
};

/**
 * The readiness severity a fault of this priority earns, or `null` for none.
 *
 * Mirrors `FaultReadinessPolicy.severityFor`. Shown on the report dialog so somebody raising a
 * critical fault in an examination hall knows, before they submit, that they are about to take the
 * hall out of service. The threshold is configurable per site, so it is passed in rather than fixed.
 */
export const faultBlockerSeverity = (
  priority: FaultPriority,
  threshold: FaultPriority = 'HIGH',
): BlockerSeverity | null => {
  if (faultPriorities.indexOf(priority) < faultPriorities.indexOf(threshold)) {
    return null;
  }
  switch (priority) {
    case 'CRITICAL':
      return 'CRITICAL';
    case 'HIGH':
      return 'MAJOR';
    case 'MEDIUM':
      return 'MINOR';
    default:
      return 'ADVISORY';
  }
};

/** Evidence may only be attached while the work order is open. */
export const attachEvidenceAction = (order: WorkOrder): Action => {
  if (!permits('FACILITIES_EVIDENCE_ATTACH')) {
    return deny(NO_PERMISSION);
  }
  return order.open
    ? allow
    : deny(`Evidence cannot be attached to a ${humanStatus(order.status)} work order.`);
};

/**
 * Exporting evidence out of CLET.
 *
 * Its own permission, held only by reviewers — SRS-SFL-S153-03 makes export a distinct authorised
 * act with a recorded reason, not a stronger form of reading.
 */
export const exportEvidenceAction = (): Action =>
  permits('FACILITIES_EVIDENCE_EXPORT') ? allow : deny(NO_PERMISSION);

/**
 * Whether the actor may ever close a work order, ignoring the record's state.
 *
 * Separate from {@link closeAction} because the two answer different questions and the screen treats
 * them differently. A **permission** denial is permanent for this session, so the control is not
 * rendered at all — a button that can never be pressed is clutter, and one that reads "you do not
 * have permission" on every visit reads as a broken screen. A **shortfall** is actionable, so that
 * control is rendered and disabled with the count.
 *
 * The distinction matters most here: a technician holds `_UPDATE` and never `_CLOSE`, so they would
 * otherwise carry a dead Close button through every job they ever do.
 */
export const canCloseWorkOrders = (): boolean => permits('FACILITIES_WORK_ORDER_CLOSE');

export const canReportFaults = (): boolean => permits('FACILITIES_FAULT_REPORT');
export const canReadFaults = (): boolean => permits('FACILITIES_FAULT_READ');
export const canReadWorkOrders = (): boolean => permits('FACILITIES_WORK_ORDER_READ');
export const canUpdateWorkOrders = (): boolean => permits('FACILITIES_WORK_ORDER_UPDATE');
export const canManageSchedules = (): boolean => permits('FACILITIES_PM_SCHEDULE_MANAGE');
export const canManageVendors = (): boolean => permits('FACILITIES_VENDOR_MANAGE');
export const canReadEvidence = (): boolean => permits('FACILITIES_EVIDENCE_READ');

/** Shared by the transitions that differ only in which move they ask for. */
const transition = (order: WorkOrder, to: WorkOrderStatus, verb: string): Action => {
  if (!permits('FACILITIES_WORK_ORDER_UPDATE')) {
    return deny(NO_PERMISSION);
  }
  return canTransitionTo(order.status, to)
    ? allow
    : deny(`A ${humanStatus(order.status)} work order cannot be ${verb}.`);
};

const humanStatus = (status: WorkOrderStatus): string =>
  status.toLowerCase().replace(/_/g, ' ');
