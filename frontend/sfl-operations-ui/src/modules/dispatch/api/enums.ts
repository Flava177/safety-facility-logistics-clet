/**
 * Enumerations mirrored from `gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model`.
 *
 * Values are the wire values — the Java enum constant names, read off the records themselves and
 * confirmed against `/v3/api-docs`. Labels are UI-only; the service never sees them.
 */

export const ITEM_DIRECTIONS = ['INBOUND', 'OUTBOUND'] as const;
export type ItemDirection = (typeof ITEM_DIRECTIONS)[number];

export const ITEM_TYPES = [
  'CONFIDENTIAL_CORRESPONDENCE',
  'CERTIFICATE',
  'SEALED_MATERIAL',
  'EXAMINATION_PAPER',
  'SEALED_BAG',
  'EXAMINATION_DEVICE',
  'ORDINARY_MAIL',
] as const;
export type ItemType = (typeof ITEM_TYPES)[number];

export const SENSITIVITIES = ['ORDINARY', 'CONFIDENTIAL', 'SECRET'] as const;
export type Sensitivity = (typeof SENSITIVITIES)[number];

export const ITEM_STATUSES = [
  'RECEIVED',
  'STAGED',
  'DISPATCHED',
  'IN_TRANSIT',
  'DELIVERED',
  'RETURNED',
  'EXCEPTION',
  'CLOSED',
] as const;
export type ItemStatus = (typeof ITEM_STATUSES)[number];

export const DISPATCH_STATUSES = [
  'DRAFT',
  'SEALED',
  'DISPATCHED',
  'IN_TRANSIT',
  'RECEIVED',
  'RETURNED',
  'RECONCILED',
  'CLOSED',
  'EXCEPTION',
] as const;
export type DispatchStatus = (typeof DISPATCH_STATUSES)[number];

/**
 * The seven custody hops, in the order a consignment passes through them.
 *
 * Order matters: `CustodyChainPolicy` reads the recorded handovers against this sequence to find
 * gaps, so the detail screen presents them in the same order rather than by when they were typed.
 */
export const CUSTODY_HOPS = [
  'WAREHOUSE_STAGING',
  'DISPATCH',
  'TRANSIT',
  'CENTRE_RECEIPT',
  'HALL_DEPLOYMENT',
  'COLLECTION',
  'RETURN',
] as const;
export type CustodyHop = (typeof CUSTODY_HOPS)[number];

export const SEAL_STATES = ['INTACT', 'BROKEN', 'REPLACED', 'MISSING'] as const;
export type SealState = (typeof SEAL_STATES)[number];

export const RECEIPT_OUTCOMES = ['CLEAN', 'VARIANCE'] as const;
export type ReceiptOutcome = (typeof RECEIPT_OUTCOMES)[number];

export const VARIANCE_TYPES = [
  'BROKEN_SEAL',
  'SHORT_COUNT',
  'OVER_COUNT',
  'WRONG_RECIPIENT',
  'MISSING_SIGNATURE',
] as const;
export type VarianceType = (typeof VARIANCE_TYPES)[number];

export const RETURN_OUTCOMES = ['MATCHED', 'DISCREPANCY'] as const;
export type ReturnOutcome = (typeof RETURN_OUTCOMES)[number];

export const MANIFEST_ITEM_RETURN_STATUSES = ['PENDING', 'RETURNED', 'OUTSTANDING'] as const;
export type ManifestItemReturnStatus = (typeof MANIFEST_ITEM_RETURN_STATUSES)[number];

export const EXCEPTION_TYPES = [
  'UNREGISTERED_ITEM',
  'CUSTODY_GAP',
  'RECEIPT_VARIANCE',
  'SCAN_MISMATCH',
  'UNDELIVERED_ITEM',
  'RETURN_DISCREPANCY',
] as const;
export type ExceptionType = (typeof EXCEPTION_TYPES)[number];

export const EXCEPTION_SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type ExceptionSeverity = (typeof EXCEPTION_SEVERITIES)[number];

export const EXCEPTION_STATUSES = [
  'DETECTED',
  'ASSIGNED',
  'UNDER_REVIEW',
  'AWAITING_EXPLANATION',
  'EXPLANATION_RECEIVED',
  'APPROVED',
  'REJECTED',
  'ESCALATED',
  'CLOSED',
  'HELD',
  'CANCELLED',
  'REOPENED',
] as const;
export type ExceptionStatus = (typeof EXCEPTION_STATUSES)[number];

export const EXCEPTION_DECISIONS = ['APPROVED', 'REJECTED'] as const;
export type ExceptionDecision = (typeof EXCEPTION_DECISIONS)[number];

export const SCAN_BATCH_STATUSES = ['PROCESSED', 'PARTIAL', 'FAILED'] as const;
export type ScanBatchStatus = (typeof SCAN_BATCH_STATUSES)[number];

export const SCAN_ROW_OUTCOMES = ['MATCHED', 'MISMATCH', 'UNREGISTERED'] as const;
export type ScanRowOutcome = (typeof SCAN_ROW_OUTCOMES)[number];

/**
 * Item types the domain treats as requiring a chain of custody.
 *
 * `CourierItem` derives `chainOfCustodyRequired` itself, so this list is only used to warn an
 * operator *before* they submit — the record's own flag is what any screen displays.
 */
export const CUSTODY_REQUIRED_TYPES: ItemType[] = [
  'CONFIDENTIAL_CORRESPONDENCE',
  'CERTIFICATE',
  'SEALED_MATERIAL',
  'EXAMINATION_PAPER',
  'SEALED_BAG',
  'EXAMINATION_DEVICE',
];

/** What each custody hop covers, in the operator's words. */
export const HOP_DESCRIPTIONS: Record<CustodyHop, string> = {
  WAREHOUSE_STAGING: 'Assembled and held before the consignment leaves the store.',
  DISPATCH: 'Handed to the carrying party as the consignment leaves.',
  TRANSIT: 'Passed between carriers, or held at a staging point en route.',
  CENTRE_RECEIPT: 'Received at the destination centre.',
  HALL_DEPLOYMENT: 'Moved from the centre to the hall where it is used.',
  COLLECTION: 'Collected after use, for the return leg.',
  RETURN: 'Handed back at the originating site.',
};

/** What raises each exception type. Shown on a case so the reason is legible without the rule name. */
export const EXCEPTION_TYPE_DESCRIPTIONS: Record<ExceptionType, string> = {
  UNREGISTERED_ITEM: 'A scan referenced an item that is not on the register.',
  CUSTODY_GAP: 'The chain of custody is missing a handover, or a hop is out of sequence.',
  RECEIPT_VARIANCE: 'The receipt disagreed with the manifest on seal, count or recipient.',
  SCAN_MISMATCH: 'A scanned code did not match the item the manifest expected.',
  UNDELIVERED_ITEM: 'An item was dispatched and never confirmed as delivered.',
  RETURN_DISCREPANCY: 'The return leg did not reconcile against the original manifest.',
};

/** Required CSV headers for a scan import, from `DispatchScanService`. */
export const SCAN_CSV_HEADERS = ['rowReference', 'scannedCode'] as const;
