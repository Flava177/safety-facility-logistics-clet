import { RecordMetadata, SiteCodeValue, SourceChannel } from 'modules/fuel/api/dto';
import {
  CustodyHop,
  DispatchStatus,
  ExceptionDecision,
  ExceptionSeverity,
  ExceptionStatus,
  ExceptionType,
  ItemDirection,
  ItemStatus,
  ItemType,
  ManifestItemReturnStatus,
  ReceiptOutcome,
  ReturnOutcome,
  ScanBatchStatus,
  ScanRowOutcome,
  SealState,
  Sensitivity,
  VarianceType,
} from './enums';

/**
 * Wire types for the S171 dispatch API.
 *
 * The dispatch controllers return the **domain records themselves**, exactly as fuel does, so these
 * mirror `CourierItem`, `Dispatch`, `CustodyHandover`, `DispatchReceipt`, `ReturnReconciliation`,
 * `DispatchExceptionCase` and the scan batch field for field — including `siteCode` arriving as a
 * `SiteCode` value object that serialises as `{ value }`.
 *
 * `RecordMetadata`, `SiteCodeValue` and `SourceChannel` are imported from the fuel module rather
 * than redeclared: they are the same platform types on the wire, and two copies would be two things
 * to keep in step.
 */

export type { RecordMetadata, SiteCodeValue, SourceChannel };

/** S171-01 — one tracked item, inbound or outbound. */
export interface CourierItem {
  id: string;
  itemNumber: string;
  siteCode: SiteCodeValue;
  direction: ItemDirection;
  itemType: ItemType;
  sensitivity: Sensitivity;
  /** Derived by the domain from type and sensitivity — not something the caller sets. */
  chainOfCustodyRequired: boolean;
  origin: string;
  destination: string;
  sender: string | null;
  recipient: string | null;
  assignedHandler: string | null;
  status: ItemStatus;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  acknowledgementEvidenceId: string | null;
  distributionReference: string | null;
  misrouteReason: string | null;
  undelivered: boolean;
  exceptionReason: string | null;
  metadata: RecordMetadata;
}

/** S171-02 — a manifest: the consignment, its seals and its trip assignment. */
export interface DispatchManifest {
  id: string;
  manifestNumber: string;
  siteCode: SiteCodeValue;
  route: string;
  assignedHandler: string;
  destinationCentre: string | null;
  examinationContext: string | null;
  tripId: string | null;
  vehicleId: string | null;
  driverId: string | null;
  itemCount: number;
  sealIds: string[];
  status: DispatchStatus;
  dispatchedAt: string | null;
  receivedAt: string | null;
  reconciledAt: string | null;
  closureReason: string | null;
  metadata: RecordMetadata;
}

/** One line of a manifest, with what the return leg made of it. */
export interface DispatchManifestItem {
  id: string;
  dispatchId: string;
  courierItemId: string;
  siteCode: SiteCodeValue;
  sequenceNo: number;
  expectedSealId: string | null;
  expectedQuantity: number;
  returnStatus: ManifestItemReturnStatus;
  returnedAt: string | null;
  returnSealState: SealState | null;
  createdAt: string;
}

/** An append-only custody handover. There is no update or delete — the chain is the evidence. */
export interface CustodyHandover {
  id: string;
  dispatchId: string;
  siteCode: SiteCodeValue;
  hop: CustodyHop;
  sequenceNo: number;
  transferringCustodian: string;
  receivingCustodian: string;
  occurredAt: string;
  sealState: SealState;
  verifiedCount: number | null;
  notes: string | null;
  evidenceId: string | null;
  createdBy: string;
  createdAt: string;
  sourceChannel: SourceChannel;
  correlationId: string | null;
}

/** What `CustodyChainPolicy` makes of the recorded handovers. `closable` gates manifest closure. */
export interface CustodyGaps {
  gaps: string[];
  missingClosureHops: CustodyHop[];
  closable: boolean;
}

/** S171-03 — destination receipt, with the variance the policy derived. */
export interface DispatchReceipt {
  id: string;
  dispatchId: string;
  siteCode: SiteCodeValue;
  sealState: SealState;
  sealVerified: boolean;
  expectedCount: number | null;
  verifiedCount: number;
  recipientName: string;
  signatureEvidenceId: string | null;
  /** Derived by `ReceiptVariancePolicy`; never supplied by the caller. */
  outcome: ReceiptOutcome;
  varianceType: VarianceType | null;
  capturedAt: string | null;
  edgeCaptured: boolean;
  captureCorrelationId: string | null;
  reconciledAt: string | null;
  metadata: RecordMetadata;
}

/** S171-06 — the return leg measured against the original manifest. */
export interface ReturnReconciliation {
  id: string;
  dispatchId: string;
  siteCode: SiteCodeValue;
  expectedCount: number | null;
  returnedCount: number;
  shortfall: number;
  extras: number;
  brokenSeals: number;
  outcome: ReturnOutcome;
  notes: string | null;
  evidenceId: string | null;
  reconciledBy: string;
  reconciledAt: string;
  metadata: RecordMetadata;
}

/** The accountable exception workflow, shaped exactly like the fuel anomaly case. */
export interface DispatchExceptionCase {
  id: string;
  exceptionNumber: string;
  siteCode: SiteCodeValue;
  /** Stable per occurrence, so a repeated detection updates one case rather than raising many. */
  occurrenceKey: string;
  courierItemId: string | null;
  dispatchId: string | null;
  handoverId: string | null;
  receiptId: string | null;
  tripId: string | null;
  type: ExceptionType;
  severity: ExceptionSeverity;
  /** Escalation of a security-relevant case is surfaced to the security function. */
  securityRelevant: boolean;
  status: ExceptionStatus;
  assignee: string | null;
  slaDueAt: string;
  explanation: string | null;
  evidenceId: string | null;
  decision: ExceptionDecision | null;
  closureReason: string | null;
  escalationLevel: number;
  detectedRules: string[];
  metadata: RecordMetadata;
}

export interface ScanImportBatch {
  id: string;
  siteCode: SiteCodeValue;
  batchReference: string | null;
  sourceSystem: string;
  dispatchId: string | null;
  totalRows: number;
  acceptedRows: number;
  mismatchRows: number;
  status: ScanBatchStatus;
  metadata: RecordMetadata;
}

export interface ScanImportRow {
  id: string;
  batchId: string;
  siteCode: SiteCodeValue;
  rowReference: string;
  scannedCode: string;
  courierItemId: string | null;
  outcome: ScanRowOutcome;
  message: string | null;
  createdAt: string;
}

/* ---------------------------------------------------------------- requests */

export interface RegisterItemRequest {
  siteCode: string;
  /** Optional — the service allocates a number when it is blank. */
  itemNumber: string | null;
  direction: ItemDirection;
  itemType: ItemType;
  sensitivity: Sensitivity;
  origin: string;
  destination: string;
  sender: string | null;
  recipient: string | null;
  assignedHandler: string | null;
}

export interface RegisterInboundRequest {
  siteCode: string;
  itemNumber: string | null;
  itemType: ItemType;
  sensitivity: Sensitivity;
  origin: string;
  destination: string;
  sender: string | null;
  recipient: string | null;
  assignedHandler: string | null;
}

export interface DistributeInboundRequest {
  acknowledgedBy: string;
  distributionReference: string | null;
  signatureFileName: string | null;
  signatureContentType: string | null;
  signatureStorageReference: string | null;
  signatureSha256: string | null;
  retentionClass: string | null;
}

export interface MisrouteRequest {
  reason: string;
  handler: string | null;
}

export interface CreateManifestRequest {
  siteCode: string;
  manifestNumber: string | null;
  route: string;
  assignedHandler: string;
  destinationCentre: string | null;
  examinationContext: string | null;
  tripId: string | null;
  vehicleId: string | null;
  driverId: string | null;
}

export interface AddManifestItemRequest {
  courierItemId: string;
  expectedSealId: string | null;
  /** Primitive `int` on the service — never send null. */
  expectedQuantity: number;
}

export interface SealRequest {
  sealIds: string[];
}

export interface AssignTripRequest {
  tripId: string | null;
  vehicleId: string | null;
  driverId: string | null;
}

export interface CloseManifestRequest {
  reason: string;
}

export interface RecordHandoverRequest {
  dispatchId: string;
  hop: CustodyHop;
  transferringCustodian: string;
  receivingCustodian: string;
  occurredAt: string | null;
  sealState: SealState;
  verifiedCount: number | null;
  notes: string | null;
  evidenceFileName: string | null;
  evidenceContentType: string | null;
  evidenceStorageReference: string | null;
  evidenceSha256: string | null;
  retentionClass: string | null;
}

export interface ConfirmReceiptRequest {
  dispatchId: string;
  sealState: SealState;
  sealVerified: boolean;
  expectedCount: number | null;
  /** Primitive `int`, `@PositiveOrZero`. */
  verifiedCount: number;
  recipientName: string;
  expectedRecipient: string | null;
  captureCorrelationId: string | null;
  /** Primitive `boolean` — marks a capture taken offline at the edge and replayed. */
  edgeCaptured: boolean;
  capturedAt: string | null;
  signatureFileName: string | null;
  signatureContentType: string | null;
  signatureStorageReference: string | null;
  signatureSha256: string | null;
  retentionClass: string | null;
}

export interface ReconcileReturnRequest {
  dispatchId: string;
  expectedCount: number | null;
  verifiedCountUnused?: never;
  /** Primitive `int`, `@PositiveOrZero`. */
  returnedCount: number;
  /** Primitive `int`, `@PositiveOrZero`. */
  brokenSeals: number;
  notes: string | null;
  evidenceFileName: string | null;
  evidenceContentType: string | null;
  evidenceStorageReference: string | null;
  evidenceSha256: string | null;
  retentionClass: string | null;
}

/** `DispatchExceptionController.ActionRequest` — `value` carries assignee, explanation or reason. */
export interface ExceptionActionRequest {
  value: string | null;
  evidenceId: string | null;
}

/* ---------------------------------------------------------------- queries */

export interface ItemSearchParams {
  siteCode: string;
  direction?: ItemDirection | '';
  status?: ItemStatus | '';
  sensitivity?: Sensitivity | '';
  handler?: string;
  from?: string;
  to?: string;
  size?: number;
}

export interface InboundSearchParams {
  siteCode: string;
  status?: ItemStatus | '';
  handler?: string;
  from?: string;
  to?: string;
  size?: number;
}

export interface ManifestSearchParams {
  siteCode: string;
  status?: DispatchStatus | '';
  destinationCentre?: string;
  tripId?: string;
  from?: string;
  to?: string;
  size?: number;
}

export interface ExceptionSearchParams {
  siteCode: string;
  type?: ExceptionType | '';
  status?: ExceptionStatus | '';
  size?: number;
}

/* -------------------------------------------------------------- dashboard */

/**
 * `GET /api/v1/dispatch/dashboard` — the whole payload.
 *
 * Eight counts plus provenance. Unlike the fuel dashboard these are all exception-shaped: the
 * screen's job is to show what is going wrong, and the volume figures have to come from the
 * registers.
 */
export interface DispatchDashboardSnapshot {
  inTransitCount: number;
  openExceptionCount: number;
  custodyGapCount: number;
  receiptVarianceCount: number;
  outstandingReturnCount: number;
  undeliveredCount: number;
  overdueReceiptCount: number;
  slaBreachCount: number;
  sourceUpdatedAt: string | null;
  stale: boolean;
  generatedAt: string;
}

/* ------------------------------------------------------------ integration */

export interface DispatchIntegrationHealth {
  inbox: {
    checkedAt: string;
    processedMessages: number;
    rejectedMessages: number;
    deadLetterMessages: number;
    recentMessages: {
      id: string;
      sourceSystem: string;
      idempotencyKey: string | null;
      eventType: string;
      siteCode: string | null;
      status: 'ACCEPTED' | 'PROCESSED' | 'REJECTED' | 'DEAD_LETTER';
      attempts: number;
      receivedAt: string;
      processedAt: string | null;
    }[];
  };
  outbox: {
    pending: number;
    published: number;
    deadLettered: number;
    recentDeadLetters: {
      id: string;
      eventType: string;
      aggregateType: string;
      aggregateId: string;
      status: string;
      attemptCount: number;
      failureReason: string | null;
      createdAt: string;
    }[];
  };
}
