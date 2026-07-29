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

export interface TransactionSearchParams {
  siteCode: string;
  status?: FuelTransactionStatus | '';
  vehicleId?: string;
  driverId?: string;
  from?: string;
  to?: string;
  size?: number;
}

export interface LogbookSearchParams {
  siteCode: string;
  status?: LogbookStatus | '';
  size?: number;
}

export interface AnomalySearchParams {
  siteCode: string;
  status?: AnomalyStatus | '';
  size?: number;
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

/** `FuelImportService.ImportResult` — returned by the upload and never readable again (gap 2). */
export interface ImportResult {
  batchId: string;
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
  rows: ImportRowResult[];
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
 * Five figures from the `fuel_dashboard_summary` view over `fuel_transactions`, the view's own
 * high-water mark, and a staleness flag the service computes against a 15-minute threshold. There
 * are no anomaly, logbook or import indicators in it; see gap 6.
 */
export interface FuelDashboardSnapshot {
  transactionCount: number;
  fuelVolume: number | null;
  fuelSpend: number | null;
  reconciledCount: number;
  exceptionCount: number;
  sourceUpdatedAt: string | null;
  stale: boolean;
}
