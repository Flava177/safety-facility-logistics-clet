import { CourierItem, CustodyGaps, DispatchExceptionCase, DispatchManifest } from './dto';
import { ExceptionAction, ItemAction } from './dispatchApi';

/**
 * The dispatch lifecycles, transcribed from the domain records' own `requireState(...)` guards.
 *
 * A **mirror, never a replacement**. Every transition still submits and the service still decides;
 * what this buys is that a screen offers "Seal" only where sealing is legal, and can say *why* an
 * action is unavailable before the operator spends a round trip finding out.
 *
 * Transcribed from `CourierItem`, `Dispatch`, `DispatchExceptionCase`, `DispatchClosurePolicy` and
 * `CustodyChainPolicy` in `gh.edu.clet.sfl.fleetlogistics.dispatch`.
 */

export interface TransitionRule {
  /** Statuses `requireState(...)` accepts. Empty means the domain imposes no state guard. */
  from: string[];
  label: string;
  requiredField: 'reason' | 'assignee' | 'explanation' | 'handler' | null;
  requiresEvidence?: boolean;
  permission: string;
  privileged?: boolean;
}

/* ------------------------------------------------------------- courier item */

export const ITEM_RULES: Record<ItemAction, TransitionRule> = {
  stage: {
    from: ['RECEIVED'],
    label: 'Stage',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
  dispatch: {
    from: ['RECEIVED', 'STAGED'],
    label: 'Dispatch',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
  'in-transit': {
    from: ['DISPATCHED'],
    label: 'Mark in transit',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
  deliver: {
    from: ['DISPATCHED', 'IN_TRANSIT'],
    label: 'Mark delivered',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
  return: {
    from: ['DELIVERED'],
    label: 'Return to origin',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
  close: {
    from: ['DELIVERED', 'RETURNED'],
    label: 'Close',
    requiredField: null,
    permission: 'DISPATCH_ITEM_MANAGE',
  },
};

export const itemActionAllowed = (item: CourierItem, action: ItemAction): boolean =>
  ITEM_RULES[action].from.includes(item.status);

/** Misroute has no state guard of its own; it is refused only on a closed item. */
export const itemMisroutable = (item: CourierItem): boolean => item.status !== 'CLOSED';

/** Distribution is the inbound closing move, legal only before the item leaves the mailroom. */
export const itemDistributable = (item: CourierItem): boolean =>
  item.direction === 'INBOUND' && ['RECEIVED', 'STAGED'].includes(item.status);

export const itemLive = (item: CourierItem): boolean =>
  item.status !== 'CLOSED' && item.status !== 'EXCEPTION';

/* ---------------------------------------------------------------- manifest */

export type ManifestAction =
  | 'addItem'
  | 'seal'
  | 'assignTrip'
  | 'dispatch'
  | 'inTransit'
  | 'close';

export const MANIFEST_RULES: Record<ManifestAction, TransitionRule> = {
  // `updateManifest` is draft-only, so the contents freeze the moment the manifest is sealed.
  addItem: {
    from: ['DRAFT'],
    label: 'Add item',
    requiredField: null,
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
  seal: {
    from: ['DRAFT'],
    label: 'Seal manifest',
    requiredField: null,
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
  assignTrip: {
    from: ['DRAFT', 'SEALED'],
    label: 'Assign trip',
    requiredField: null,
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
  dispatch: {
    from: ['SEALED'],
    label: 'Dispatch',
    requiredField: null,
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
  inTransit: {
    from: ['DISPATCHED'],
    label: 'Mark in transit',
    requiredField: null,
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
  close: {
    from: ['RECEIVED', 'RETURNED', 'RECONCILED'],
    label: 'Close manifest',
    requiredField: 'reason',
    permission: 'DISPATCH_MANIFEST_CREATE',
  },
};

export const manifestActionAllowed = (
  manifest: DispatchManifest,
  action: ManifestAction,
): boolean => MANIFEST_RULES[action].from.includes(manifest.status);

/** Receipt confirmation is what moves a manifest to RECEIVED, from dispatched or in transit. */
export const manifestReceivable = (manifest: DispatchManifest): boolean =>
  ['DISPATCHED', 'IN_TRANSIT'].includes(manifest.status);

/** The return leg is reconciled once the consignment has been received back. */
export const manifestReturnReconcilable = (manifest: DispatchManifest): boolean =>
  ['RECEIVED', 'RETURNED'].includes(manifest.status);

export const manifestActive = (manifest: DispatchManifest): boolean =>
  manifest.status !== 'CLOSED' && manifest.status !== 'EXCEPTION';

/**
 * What `DispatchClosurePolicy` will refuse a closure for.
 *
 * Two conditions, and the operator needs both named rather than one combined message: an open
 * exception case, and a custody chain that is not closable. The second is itself two things — a
 * recorded gap, or a required hop that was never recorded — which `CustodyGaps` separates.
 */
export const manifestClosureBlockers = (
  custody: CustodyGaps | undefined,
  openExceptions: number,
): string[] => {
  const blockers: string[] = [];
  if (openExceptions > 0) {
    blockers.push(
      `${openExceptions} exception case${openExceptions === 1 ? '' : 's'} against this manifest ${
        openExceptions === 1 ? 'is' : 'are'
      } still open.`,
    );
  }
  if (custody && !custody.closable) {
    if (custody.gaps.length > 0) {
      blockers.push(
        `The custody chain has ${custody.gaps.length} recorded gap${custody.gaps.length === 1 ? '' : 's'}.`,
      );
    }
    if (custody.missingClosureHops.length > 0) {
      blockers.push(
        `No handover was recorded for ${custody.missingClosureHops.length} hop${
          custody.missingClosureHops.length === 1 ? '' : 's'
        } that closure requires.`,
      );
    }
  }
  return blockers;
};

/* --------------------------------------------------------------- exception */

export const EXCEPTION_RULES: Record<ExceptionAction, TransitionRule> = {
  assign: {
    from: ['DETECTED', 'REOPENED'],
    label: 'Assign',
    requiredField: 'assignee',
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  reassign: {
    from: ['ASSIGNED', 'UNDER_REVIEW', 'AWAITING_EXPLANATION', 'EXPLANATION_RECEIVED', 'HELD'],
    label: 'Reassign',
    requiredField: 'assignee',
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  review: {
    from: ['ASSIGNED', 'EXPLANATION_RECEIVED'],
    label: 'Start review',
    requiredField: null,
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  'request-explanation': {
    from: ['UNDER_REVIEW'],
    label: 'Request explanation',
    requiredField: null,
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  explain: {
    from: ['AWAITING_EXPLANATION'],
    label: 'Record explanation',
    requiredField: 'explanation',
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  approve: {
    from: ['UNDER_REVIEW'],
    label: 'Approve',
    requiredField: 'reason',
    permission: 'DISPATCH_EXCEPTION_APPROVE',
    privileged: true,
  },
  reject: {
    from: ['UNDER_REVIEW'],
    label: 'Reject',
    requiredField: 'reason',
    permission: 'DISPATCH_EXCEPTION_APPROVE',
    privileged: true,
  },
  escalate: {
    // No `requireState` guard — legal from any status, as in the fuel anomaly case.
    from: [],
    label: 'Escalate',
    requiredField: 'reason',
    permission: 'DISPATCH_EXCEPTION_ESCALATE',
    privileged: true,
  },
  hold: {
    from: ['ASSIGNED', 'UNDER_REVIEW', 'AWAITING_EXPLANATION', 'EXPLANATION_RECEIVED'],
    label: 'Place on hold',
    requiredField: 'reason',
    permission: 'DISPATCH_EXCEPTION_MANAGE',
  },
  resume: {
    from: ['HELD'],
    label: 'Resume',
    requiredField: null,
    permission: 'DISPATCH_EXCEPTION_MANAGE',
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
    permission: 'DISPATCH_EXCEPTION_MANAGE',
    privileged: true,
  },
  close: {
    from: ['APPROVED', 'REJECTED', 'ESCALATED'],
    label: 'Close',
    requiredField: 'reason',
    requiresEvidence: true,
    permission: 'DISPATCH_EXCEPTION_APPROVE',
    privileged: true,
  },
  reopen: {
    from: ['CLOSED'],
    label: 'Reopen',
    requiredField: 'reason',
    permission: 'DISPATCH_EXCEPTION_MANAGE',
    privileged: true,
  },
};

export const exceptionActionAllowed = (
  exceptionCase: DispatchExceptionCase,
  action: ExceptionAction,
): boolean => {
  const rule = EXCEPTION_RULES[action];
  return rule.from.length === 0 || rule.from.includes(exceptionCase.status);
};

/** Closure needs an explanation and a decision already recorded, plus evidence with the closure. */
export const exceptionClosureBlockers = (exceptionCase: DispatchExceptionCase): string[] => {
  const blockers: string[] = [];
  if (!exceptionCase.explanation) {
    blockers.push(
      'No explanation is recorded. Request one, then record the response before closing.',
    );
  }
  if (!exceptionCase.decision) {
    blockers.push('No decision is recorded. Approve or reject the case before closing.');
  }
  return blockers;
};

export const exceptionOpen = (exceptionCase: DispatchExceptionCase): boolean =>
  exceptionCase.status !== 'CLOSED' && exceptionCase.status !== 'CANCELLED';

export const exceptionSlaBreached = (
  exceptionCase: DispatchExceptionCase,
  now = Date.now(),
): boolean => exceptionOpen(exceptionCase) && new Date(exceptionCase.slaDueAt).getTime() < now;
