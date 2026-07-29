import {
  AnomalyDecision,
  AnomalySeverity,
  AnomalyStatus,
  AnomalyType,
  FuelPolicyStatus,
  FuelTransactionLifecycle,
  FuelTransactionStatus,
  LogbookStatus,
  LogbookUseClassification,
} from './enums';

/**
 * Wire types for the S168 fuel API.
 *
 * The fuel controllers return the **domain records themselves** rather than response DTOs, so these
 * mirror `FuelTransaction`, `DriverLogbook`, `FuelAnomalyCase` and `FuelPolicy` field for field —
 * including the two shapes that catch people out: `siteCode` is a `SiteCode` value object that
 * serialises as `{ value }`, and `currency` is a `java.util.Currency` that serialises as an object,
 * not a string. Requests, by contrast, take a plain `String` for both.
 */

/**
 * The fuel collection envelope.
 *
 * Identical in shape to the fleet `PageResponse`, and new: every fuel collection used to return a
 * bare array capped by a `size` limit, with no total and no way to tell a full register from the
 * first hundred rows of it. `sort` is echoed back because a request need not name one.
 */
export interface FuelPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort: string | null;
}

export const emptyFuelPage = <T,>(size = 25): FuelPageResponse<T> => ({
  content: [],
  page: 0,
  size,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  sort: null,
});

/** Paging parameters every fuel collection accepts. `sort` is `field` or `field,asc|desc`. */
export interface FuelPageParams {
  page?: number;
  size?: number;
  sort?: string;
}

/** `gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode` on the wire. */
export interface SiteCodeValue {
  value: string;
}

/** `java.util.Currency` as Jackson renders it. Only `currencyCode` is ever displayed. */
export interface CurrencyValue {
  currencyCode: string;
  displayName?: string;
  symbol?: string;
  defaultFractionDigits?: number;
  numericCode?: number;
}

export type SourceChannel =
  | 'WEB'
  | 'MOBILE'
  | 'API'
  | 'INTEGRATION'
  | 'SCHEDULER'
  | 'SYSTEM'
  | 'IMPORT'
  | 'MIGRATION';

/** `RecordMetadata` — provenance carried by every fuel aggregate. */
export interface RecordMetadata {
  createdBy: string | null;
  createdAt: string | null;
  lastModifiedBy: string | null;
  lastModifiedAt: string | null;
  version: number;
  sourceChannel: SourceChannel;
  auditCorrelationId: string | null;
}

export interface FuelPolicy {
  id: string;
  siteCode: SiteCodeValue;
  name: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  policyVersion: number;
  maxPerTransaction: number;
  dailyLimit: number | null;
  monthlyLimit: number | null;
  tankCapacity: number | null;
  minConsumption: number | null;
  maxConsumption: number | null;
  odometerJumpTolerance: number;
  receiptRequired: boolean;
  receiptGraceHours: number;
  materialityAmount: number;
  anomalySlaHours: number;
  allowedFuelProducts: string[];
  approvedVendors: string[];
  status: FuelPolicyStatus;
  metadata: RecordMetadata;
}

export interface FuelTransaction {
  id: string;
  siteCode: SiteCodeValue;
  providerTransactionId: string | null;
  sourceSystem: string;
  vehicleId: string;
  driverId: string;
  tripId: string | null;
  occurredAt: string;
  vendorReference: string;
  stationReference: string | null;
  fuelProduct: string;
  quantity: number;
  quantityUnit: string;
  unitPrice: number;
  totalCost: number;
  currency: CurrencyValue;
  /** Already masked by the domain record — never the full card number. */
  maskedCardReference: string | null;
  odometerReading: number;
  receiptEvidenceId: string | null;
  comments: string | null;
  status: FuelTransactionStatus;
  lifecycle: FuelTransactionLifecycle;
  ingestionTimestamp: string;
  idempotencyKey: string | null;
  metadata: RecordMetadata;
}

export interface DriverLogbook {
  id: string;
  logbookNumber: string;
  siteCode: SiteCodeValue;
  driverId: string;
  vehicleId: string;
  tripId: string | null;
  journeyDate: string;
  startTime: string;
  endTime: string | null;
  origin: string;
  destination: string;
  routeNotes: string | null;
  useClassification: LogbookUseClassification;
  purpose: string;
  passengerLoadNotes: string | null;
  startOdometer: number;
  endOdometer: number | null;
  declarationAccepted: boolean;
  evidenceId: string | null;
  status: LogbookStatus;
  reviewComment: string | null;
  transitionReason: string | null;
  submittedAt: string | null;
  approvedAt: string | null;
  metadata: RecordMetadata;
}

export interface FuelAnomalyCase {
  id: string;
  anomalyNumber: string;
  siteCode: SiteCodeValue;
  transactionId: string | null;
  logbookId: string | null;
  vehicleId: string | null;
  driverId: string | null;
  tripId: string | null;
  type: AnomalyType;
  severity: AnomalySeverity;
  /** `totalCost >= policy.materialityAmount` at detection — drives finance/audit visibility. */
  material: boolean;
  status: AnomalyStatus;
  assignee: string | null;
  slaDueAt: string;
  explanation: string | null;
  evidenceId: string | null;
  decision: AnomalyDecision | null;
  closureReason: string | null;
  escalationLevel: number;
  detectedRules: string[];
  metadata: RecordMetadata;
}

/* ---------------------------------------------------------------- requests */

export interface CreatePolicyRequest {
  siteCode: string;
  name: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  policyVersion: number;
  maxPerTransaction: number;
  dailyLimit: number | null;
  monthlyLimit: number | null;
  tankCapacity: number | null;
  minConsumption: number | null;
  maxConsumption: number | null;
  /** Primitive `long` on the service — sending `null` fails deserialisation before validation runs. */
  odometerJumpTolerance: number;
  receiptRequired: boolean;
  receiptGraceHours: number;
  materialityAmount: number;
  anomalySlaHours: number;
  allowedFuelProducts: string[];
  approvedVendors: string[];
}

export interface CaptureTransactionRequest {
  siteCode: string;
  providerTransactionId: string | null;
  sourceSystem: string;
  vehicleId: string;
  driverId: string;
  tripId: string | null;
  occurredAt: string;
  vendorReference: string;
  stationReference: string | null;
  fuelProduct: string;
  quantity: number;
  quantityUnit: string;
  unitPrice: number;
  /** Omitted lets the service compute it; supplied, it must equal quantity × unitPrice to 2dp. */
  totalCost: number | null;
  currency: string;
  cardReference: string | null;
  /** Primitive `long`. */
  odometerReading: number;
  receiptEvidenceId: string | null;
  comments: string | null;
}

export interface CreateLogbookRequest {
  siteCode: string;
  driverId: string;
  vehicleId: string;
  tripId: string | null;
  journeyDate: string;
  startTime: string;
  endTime: string | null;
  origin: string;
  destination: string;
  routeNotes: string | null;
  useClassification: LogbookUseClassification;
  purpose: string;
  passengerLoadNotes: string | null;
  /** Primitive `long`. */
  startOdometer: number;
  endOdometer: number | null;
  /** Primitive `boolean`. Submission is refused without it. */
  declarationAccepted: boolean;
  evidenceId: string | null;
}

/** `DriverLogbookController.TransitionRequest`. */
export interface LogbookTransitionRequest {
  comment: string | null;
}

/** `FuelAnomalyController.ActionRequest` — `value` carries assignee, explanation or reason. */
export interface AnomalyActionRequest {
  value: string | null;
  evidenceId: string | null;
}

export interface VoidTransactionRequest {
  reason: string;
}

/* ---------------------------------------------------------------- queries */

export interface TransactionSearchParams extends FuelPageParams {
  siteCode: string;
  status?: FuelTransactionStatus | '';
  vehicleId?: string;
  driverId?: string;
  /** Exact match. `MANUAL` is what this dashboard writes; imports and providers write their own. */
  sourceSystem?: string;
  /** Contains-match, case insensitive. */
  vendorReference?: string;
  from?: string;
  to?: string;
}

export interface LogbookSearchParams extends FuelPageParams {
  siteCode: string;
  status?: LogbookStatus | '';
  driverId?: string;
  vehicleId?: string;
  useClassification?: LogbookUseClassification | '';
  journeyFrom?: string;
  journeyTo?: string;
}

export interface AnomalySearchParams extends FuelPageParams {
  siteCode: string;
  status?: AnomalyStatus | '';
  type?: AnomalyType | '';
  severity?: AnomalySeverity | '';
  /** Contains-match on the assignee. */
  assignee?: string;
  /** `true` for cases nobody owns, `false` for cases that have an owner. */
  unassigned?: boolean;
  material?: boolean;
  /** Neither closed nor cancelled. */
  openOnly?: boolean;
  /** SLA cutoff — with `openOnly`, this is the breaching-SLA queue. */
  dueBefore?: string;
  transactionId?: string;
}

export interface PolicySearchParams extends FuelPageParams {
  siteCode: string;
  status?: FuelPolicyStatus | '';
  /** An interval test, not a status: an active policy whose period has not started is not in force. */
  inForceOnly?: boolean;
}

export interface ImportSearchParams extends FuelPageParams {
  siteCode: string;
  sourceSystem?: string;
}

/* --------------------------------------------------------------- reconciliation */

/**
 * One reconciliation run, with the policy version it applied and every rule outcome.
 *
 * `ruleResults` is the map the service stores — `{ RULE_NAME: { passed: boolean } }` — so a screen
 * can finally show the rules that **passed** as well as the ones that failed.
 */
export interface FuelReconciliation {
  id: string;
  transactionId: string;
  policyId: string | null;
  policyVersion: number | null;
  outcome: string;
  calculatedConsumption: number | null;
  evaluatedAt: string;
  evaluatedBy: string;
  ruleResults: Record<string, { passed?: boolean } | unknown>;
  correlationId: string | null;
}

/** `{ passed: true }` and nothing else counts as a pass, matching the service's own reading. */
export const rulePassed = (value: unknown): boolean =>
  typeof value === 'object' && value !== null && (value as { passed?: boolean }).passed === true;

/* ------------------------------------------------------------------------ audit */

/** One entry of the hash-chained audit log, as the fuel history endpoints return it. */
export interface FuelAuditEvent {
  id: string;
  sequenceNo: number;
  siteScope: SiteCodeValue | null;
  actorId: string;
  actorDisplayName: string | null;
  action: string;
  resourceType: string;
  resourceId: string;
  beforeValue: unknown;
  afterValue: unknown;
  correlationId: string | null;
  sourceChannel: SourceChannel;
  occurredAt: string;
}

/* ---------------------------------------------------------------- imports */

/** `FuelImportService.RowResult`. `status` is `ACCEPTED` or `REJECTED`. */
export interface ImportRowResult {
  rowNumber: number;
  status: string;
  transactionId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

/** `FuelImportService.ImportResult` — the upload response. The batch is now readable afterwards. */
export interface ImportResult {
  batchId: string;
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
  rows: ImportRowResult[];
}

/** One row of a stored import batch. `id` distinguishes it from the upload-response shape. */
export interface FuelImportRow {
  id: string;
  rowNumber: number;
  status: 'ACCEPTED' | 'REJECTED';
  transactionId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

/** A stored import batch. `rows` is empty on a list read and populated on a detail read. */
export interface FuelImportBatch {
  id: string;
  siteCode: SiteCodeValue;
  sourceSystem: string;
  fileName: string;
  fileHash: string;
  status: 'COMPLETED' | 'COMPLETED_WITH_ERRORS';
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
  submittedBy: string;
  submittedAt: string;
  correlationId: string | null;
  rows: FuelImportRow[];
}

/* ------------------------------------------------------------ integration */

export interface IntegrationMessageSummary {
  id: string;
  sourceSystem: string;
  idempotencyKey: string | null;
  eventType: string;
  siteCode: string | null;
  status: 'ACCEPTED' | 'PROCESSED' | 'REJECTED' | 'DEAD_LETTER';
  attempts: number;
  receivedAt: string;
  processedAt: string | null;
}

export interface IntegrationHealth {
  checkedAt: string;
  processedMessages: number;
  rejectedMessages: number;
  deadLetterMessages: number;
  recentMessages: IntegrationMessageSummary[];
}

export interface OutboxEntry {
  id: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  status: string;
  attemptCount: number;
  failureReason: string | null;
  createdAt: string;
}

export interface OutboxHealth {
  pending: number;
  published: number;
  deadLettered: number;
  recentDeadLetters: OutboxEntry[];
}

export interface ReplayResult {
  messageId: string;
  requeued: boolean;
}

/* -------------------------------------------------------------- dashboard */

/**
 * `GET /api/v1/fuel/dashboard` — the whole payload.
 *
 * The five transaction figures come from the `fuel_dashboard_summary` view; the anomaly, logbook
 * and import indicators are counted by the service. Every figure here is published, so nothing on
 * the dashboard page has to be derived by this application any more.
 */
export interface FuelDashboardSnapshot {
  transactionCount: number;
  fuelVolume: number | null;
  fuelSpend: number | null;
  reconciledCount: number;
  exceptionCount: number;
  sourceUpdatedAt: string | null;
  stale: boolean;

  awaitingReconciliation: number;

  openAnomalies: number;
  anomaliesBreachingSla: number;
  materialOpenAnomalies: number;
  unassignedAnomalies: number;

  pendingLogbookReviews: number;
  draftLogbooks: number;

  importBatches: number;
  importBatchesWithErrors: number;
  lastImportAt: string | null;
}
