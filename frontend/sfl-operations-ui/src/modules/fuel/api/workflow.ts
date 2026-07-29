import { DriverLogbook, FuelAnomalyCase, FuelTransaction } from './dto';
import { AnomalyAction, LogbookTransition } from './fuelApi';

/**
 * The fuel lifecycles, transcribed from the domain records' own `requireState(...)` guards.
 *
 * This is a **mirror, never a replacement**. Every transition still submits and the service still
 * decides; what this buys is that the dashboard offers "Approve" only where approve is actually legal,
 * and can say *why* an action is unavailable before the operator spends a round trip finding out.
 *
 * Transcribed from `DriverLogbook`, `FuelAnomalyCase` and `FuelTransaction` in
 * `gh.edu.clet.sfl.fleetlogistics.fuel.domain.model`. Where the state model document and the code
 * disagree, the code wins — see gap 7 in the frontend gap register.
 */

export interface TransitionRule {
  /** Statuses `requireState(...)` accepts. Empty means the domain imposes no state guard. */
  from: string[];
  label: string;
  /** Free-text the service requires; `null` where the field is optional or unused. */
  requiredField: 'reason' | 'comment' | 'assignee' | 'explanation' | null;
  requiresEvidence?: boolean;
  /** Refused for an actor without the named permission. Shown in the dialog. */
  permission: string;
  privileged?: boolean;
}

/* ------------------------------------------------------------- driver logbook */

export const LOGBOOK_RULES: Record<LogbookTransition, TransitionRule> = {
  submit: {
    from: ['DRAFT', 'RETURNED', 'REOPENED'],
    label: 'Submit',
    requiredField: null,
    permission: 'FUEL_LOGBOOK_SUBMIT',
  },
  review: {
    from: ['SUBMITTED', 'RESUBMITTED'],
    label: 'Start review',
    requiredField: null,
    permission: 'FUEL_LOGBOOK_REVIEW',
  },
  return: {
    from: ['UNDER_REVIEW'],
    label: 'Return to driver',
    requiredField: 'comment',
    permission: 'FUEL_LOGBOOK_REVIEW',
  },
  approve: {
    from: ['UNDER_REVIEW'],
    label: 'Approve',
    requiredField: null,
    permission: 'FUEL_LOGBOOK_REVIEW',
  },
  reopen: {
    from: ['APPROVED'],
    label: 'Reopen',
    requiredField: 'reason',
    permission: 'FUEL_LOGBOOK_REOPEN',
    privileged: true,
  },
  cancel: {
    // Deliberately not RESUBMITTED or REOPENED: `DriverLogbook.cancelled` refuses both.
    from: ['DRAFT', 'SUBMITTED', 'RETURNED'],
    label: 'Cancel',
    requiredField: 'reason',
    permission: 'FUEL_LOGBOOK_REVIEW',
    privileged: true,
  },
};

/** Where `submit` lands. `RETURNED` resubmits rather than returning to draft (gap 7). */
export const logbookSubmitTarget = (status: string): string =>
  status === 'RETURNED' ? 'RESUBMITTED' : 'SUBMITTED';

/**
 * What `DriverLogbook.submit` demands beyond the state guard.
 *
 * `if (!declarationAccepted || endTime == null || endOdometer == null) throw ...` — so a draft
 * without a completed journey cannot be submitted, and the operator should be told which of the
 * three is missing rather than being handed "completed journey and driver declaration are required".
 */
export const logbookSubmissionBlockers = (logbook: DriverLogbook): string[] => {
  const blockers: string[] = [];
  if (!logbook.declarationAccepted) {
    blockers.push('The driver declaration has not been accepted.');
  }
  if (!logbook.endTime) {
    blockers.push('The journey has no end time.');
  }
  if (logbook.endOdometer === null || logbook.endOdometer === undefined) {
    blockers.push('The journey has no closing odometer reading.');
  }
  return blockers;
};

export const logbookTransitionAllowed = (
  logbook: DriverLogbook,
  transition: LogbookTransition,
): boolean => LOGBOOK_RULES[transition].from.includes(logbook.status);

/** An approved logbook is locked: nothing but a privileged reopen may touch it. */
export const logbookLocked = (logbook: DriverLogbook): boolean =>
  logbook.status === 'APPROVED' || logbook.status === 'CANCELLED';

/* -------------------------------------------------------------- anomaly case */

export const ANOMALY_RULES: Record<AnomalyAction, TransitionRule> = {
  assign: {
    from: ['DETECTED', 'REOPENED'],
    label: 'Assign',
    requiredField: 'assignee',
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  reassign: {
    from: ['ASSIGNED', 'UNDER_REVIEW', 'AWAITING_EXPLANATION', 'EXPLANATION_RECEIVED', 'HELD'],
    label: 'Reassign',
    requiredField: 'assignee',
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  review: {
    from: ['ASSIGNED', 'EXPLANATION_RECEIVED'],
    label: 'Start review',
    requiredField: null,
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  'request-explanation': {
    from: ['UNDER_REVIEW'],
    label: 'Request explanation',
    requiredField: null,
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  explain: {
    from: ['AWAITING_EXPLANATION'],
    label: 'Record explanation',
    requiredField: 'explanation',
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  approve: {
    from: ['UNDER_REVIEW'],
    label: 'Approve',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_APPROVE',
    privileged: true,
  },
  reject: {
    from: ['UNDER_REVIEW'],
    label: 'Reject',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_APPROVE',
    privileged: true,
  },
  escalate: {
    // `FuelAnomalyCase.escalate` has no `requireState` guard — it is legal from any status.
    from: [],
    label: 'Escalate',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_ESCALATE',
    privileged: true,
  },
  hold: {
    from: ['ASSIGNED', 'UNDER_REVIEW', 'AWAITING_EXPLANATION', 'EXPLANATION_RECEIVED'],
    label: 'Place on hold',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  resume: {
    from: ['HELD'],
    label: 'Resume',
    requiredField: null,
    permission: 'FUEL_ANOMALY_MANAGE',
  },
  cancel: {
    from: [
      'DETECTED',
      'ASSIGNED',
      'UNDER_REVIEW',
      'AWAITING_EXPLANATION',
      'EXPLANATION_RECEIVED',
      'HELD',
      'REOPENED',
    ],
    label: 'Cancel case',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_MANAGE',
    privileged: true,
  },
  close: {
    from: ['APPROVED', 'REJECTED', 'ESCALATED'],
    label: 'Close',
    requiredField: 'reason',
    requiresEvidence: true,
    permission: 'FUEL_ANOMALY_APPROVE',
    privileged: true,
  },
  reopen: {
    from: ['CLOSED'],
    label: 'Reopen',
    requiredField: 'reason',
    permission: 'FUEL_ANOMALY_MANAGE',
    privileged: true,
  },
};

export const anomalyActionAllowed = (anomaly: FuelAnomalyCase, action: AnomalyAction): boolean => {
  const rule = ANOMALY_RULES[action];
  return rule.from.length === 0 || rule.from.includes(anomaly.status);
};

/**
 * The three things `FuelAnomalyCase.close` demands beyond a legal state.
 *
 * `if (explanation == null || decision == null || evidence == null) throw ...` — an explanation and
 * a decision must already be on the record, and evidence comes with the closure itself. Showing
 * these before submission is the difference between an operator understanding the gate and an
 * operator seeing "explanation, decision and evidence are required for closure" and guessing.
 */
export const anomalyClosureBlockers = (anomaly: FuelAnomalyCase): string[] => {
  const blockers: string[] = [];
  if (!anomaly.explanation) {
    blockers.push(
      'No explanation is recorded. Request one, then record the response before closing.',
    );
  }
  if (!anomaly.decision) {
    blockers.push('No decision is recorded. Approve or reject the case before closing.');
  }
  return blockers;
};

export const anomalyOpen = (anomaly: FuelAnomalyCase): boolean =>
  anomaly.status !== 'CLOSED' && anomaly.status !== 'CANCELLED';

/** `slaDueAt` is in the past and the case is neither closed nor cancelled. */
export const anomalySlaBreached = (anomaly: FuelAnomalyCase, now = Date.now()): boolean =>
  anomalyOpen(anomaly) && new Date(anomaly.slaDueAt).getTime() < now;

/* --------------------------------------------------------------- transaction */

/**
 * `FuelTransaction.withStatus` refuses a record that is `VOIDED` or not `ACTIVE`, so reconciliation
 * is only offered on a live record. The service will also refuse it when no ACTIVE policy covers
 * `occurredAt` — that one cannot be predicted client-side, because policies are effective-dated and
 * the dashboard does not re-implement `appliesAt`.
 */
export const transactionReconcilable = (transaction: FuelTransaction): boolean =>
  transaction.lifecycle === 'ACTIVE' && transaction.status !== 'VOIDED';

export const transactionVoidable = (transaction: FuelTransaction): boolean =>
  transaction.status !== 'VOIDED';

/** Reconciliation has run and produced a verdict. */
export const transactionReconciled = (transaction: FuelTransaction): boolean =>
  transaction.status === 'RECONCILED' || transaction.status === 'EXCEPTION';
