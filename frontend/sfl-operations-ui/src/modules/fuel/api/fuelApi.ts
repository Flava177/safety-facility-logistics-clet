import { apiClient, downloadFile } from 'shared/api/client';
import { QueryParams } from 'shared/api/types';
import {
  AnomalyActionRequest,
  AnomalySearchParams,
  CaptureTransactionRequest,
  CreateLogbookRequest,
  CreatePolicyRequest,
  DriverLogbook,
  FuelAnomalyCase,
  FuelAuditEvent,
  FuelDashboardSnapshot,
  FuelImportBatch,
  FuelPageResponse,
  FuelPolicy,
  FuelReconciliation,
  FuelTransaction,
  ImportResult,
  ImportSearchParams,
  IntegrationHealth,
  LogbookSearchParams,
  LogbookTransitionRequest,
  OutboxHealth,
  PolicySearchParams,
  ReplayResult,
  TransactionSearchParams,
  VoidTransactionRequest,
} from './dto';

/**
 * Typed client for the S168 fuel API.
 *
 * Paths were taken from the controllers and confirmed against the running service's `/v3/api-docs`.
 * Nothing here calls an endpoint that is not there, and nothing invents a response shape.
 *
 * **Every collection is paged.** Fuel collections used to return a bare array capped by a `size`
 * limit, which is why the registers once filtered client-side over whatever came back and warned
 * when the window looked full. They now return `FuelPageResponse<T>` with a real total, so a
 * register shows the register.
 */

const BASE = '/api/v1/fuel';

/** Rows per page. Matches the shared `defaultPageSize` the fleet registers use. */
export const DEFAULT_PAGE_SIZE = 25;

/** The service caps a page at this; asking for more silently returns this many. */
export const MAX_PAGE_SIZE = 200;

const asQuery = (params: object | undefined): QueryParams | undefined =>
  params as QueryParams | undefined;

export const fuelPoliciesApi = {
  search: (params: PolicySearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelPolicy>>(
      `${BASE}/policies`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (policyId: string, signal?: AbortSignal) =>
    apiClient.get<FuelPolicy>(`${BASE}/policies/${policyId}`, undefined, signal),

  history: (policyId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAuditEvent[]>(`${BASE}/policies/${policyId}/history`, undefined, signal),

  /**
   * Refused with 409 `FUEL_POLICY_PERIOD_OVERLAP` when an active policy already covers part of the
   * period. The conflicting policies come back in the error's `details.conflictingPolicies`.
   */
  create: (body: CreatePolicyRequest) => apiClient.post<FuelPolicy>(`${BASE}/policies`, body),
};

export const fuelTransactionsApi = {
  search: (params: TransactionSearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelTransaction>>(
      `${BASE}/transactions`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (transactionId: string, signal?: AbortSignal) =>
    apiClient.get<FuelTransaction>(`${BASE}/transactions/${transactionId}`, undefined, signal),

  /** Every run against the transaction, newest first, each with its full rule result map. */
  reconciliations: (transactionId: string, signal?: AbortSignal) =>
    apiClient.get<FuelReconciliation[]>(
      `${BASE}/transactions/${transactionId}/reconciliations`,
      undefined,
      signal,
    ),

  history: (transactionId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAuditEvent[]>(
      `${BASE}/transactions/${transactionId}/history`,
      undefined,
      signal,
    ),

  /** Manual capture. The only fuel mutation that reads `Idempotency-Key`, so it is sent. */
  capture: (body: CaptureTransactionRequest) =>
    apiClient.post<FuelTransaction>(`${BASE}/transactions`, body),

  /**
   * Runs the policy rules and moves the record to `RECONCILED` or `EXCEPTION`.
   *
   * Refused with 409 `FLEET_INVALID_STATE_TRANSITION` when no ACTIVE policy covers `occurredAt`,
   * and when the transaction is already voided.
   */
  reconcile: (transactionId: string) =>
    apiClient.post<FuelTransaction>(`${BASE}/transactions/${transactionId}/reconcile`, undefined, {
      idempotent: false,
    }),

  /** Privileged: needs `FUEL_TRANSACTION_VOID`. The reason replaces the record's comments. */
  void: (transactionId: string, body: VoidTransactionRequest) =>
    apiClient.post<FuelTransaction>(`${BASE}/transactions/${transactionId}/void`, body, {
      idempotent: false,
    }),

  /**
   * `GET /reports/transactions.csv` — a `text/csv` download of the site's most recent transactions.
   *
   * Fetched rather than linked: the endpoint needs `FUEL_REPORT_EXPORT` and the actor comes from the
   * `X-SFL-*` headers, which a browser navigation would not send.
   */
  downloadReport: (siteCode: string) =>
    downloadFile(
      `${BASE}/reports/transactions.csv`,
      { siteCode },
      `fuel-transactions-${siteCode}.csv`,
    ),
};

/** The six transitions `DriverLogbookController` accepts, as a closed set. */
export const LOGBOOK_TRANSITIONS = [
  'submit',
  'review',
  'return',
  'approve',
  'reopen',
  'cancel',
] as const;
export type LogbookTransition = (typeof LOGBOOK_TRANSITIONS)[number];

export const driverLogbooksApi = {
  search: (params: LogbookSearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<DriverLogbook>>(
      `${BASE}/logbooks`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (logbookId: string, signal?: AbortSignal) =>
    apiClient.get<DriverLogbook>(`${BASE}/logbooks/${logbookId}`, undefined, signal),

  /** The record's transitions, from the audit log — draft through review to approval. */
  history: (logbookId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAuditEvent[]>(`${BASE}/logbooks/${logbookId}/history`, undefined, signal),

  create: (body: CreateLogbookRequest) => apiClient.post<DriverLogbook>(`${BASE}/logbooks`, body),

  /**
   * One path serves all six transitions.
   *
   * `comment` is the single free-text field the service takes; which of the domain's fields it
   * lands in depends on the action — `reviewComment` for return and approve, `transitionReason`
   * for reopen and cancel.
   */
  transition: (logbookId: string, action: LogbookTransition, body: LogbookTransitionRequest) =>
    apiClient.post<DriverLogbook>(`${BASE}/logbooks/${logbookId}/${action}`, body, {
      idempotent: false,
    }),
};

/** The thirteen transitions `FuelAnomalyController` accepts, as a closed set. */
export const ANOMALY_ACTIONS = [
  'assign',
  'reassign',
  'review',
  'request-explanation',
  'explain',
  'approve',
  'reject',
  'escalate',
  'hold',
  'resume',
  'cancel',
  'close',
  'reopen',
] as const;
export type AnomalyAction = (typeof ANOMALY_ACTIONS)[number];

export const fuelAnomaliesApi = {
  search: (params: AnomalySearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelAnomalyCase>>(
      `${BASE}/anomalies`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (anomalyId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAnomalyCase>(`${BASE}/anomalies/${anomalyId}`, undefined, signal),

  history: (anomalyId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAuditEvent[]>(`${BASE}/anomalies/${anomalyId}/history`, undefined, signal),

  /**
   * `value` is overloaded by the service: assignee for assign/reassign, explanation text for
   * explain, and the reason for every other action. `evidenceId` is read by explain and close only.
   */
  transition: (anomalyId: string, action: AnomalyAction, body: AnomalyActionRequest) =>
    apiClient.post<FuelAnomalyCase>(`${BASE}/anomalies/${anomalyId}/${action}`, body, {
      idempotent: false,
    }),
};

export const fuelImportsApi = {
  /**
   * `POST /imports/csv` — multipart, with the site and source system as query parameters.
   *
   * A file already imported for this site and source system is refused with 409
   * `FUEL_IMPORT_ALREADY_PROCESSED` **before** any row is captured.
   */
  uploadCsv: (siteCode: string, sourceSystem: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postForm<ImportResult>(`${BASE}/imports/csv`, form, {
      query: { siteCode, sourceSystem },
    });
  },

  /** Past batches for the site, headers only. */
  search: (params: ImportSearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelImportBatch>>(
      `${BASE}/imports`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  /** One batch with every row outcome and its retained validation error. */
  findById: (batchId: string, signal?: AbortSignal) =>
    apiClient.get<FuelImportBatch>(`${BASE}/imports/${batchId}`, undefined, signal),
};

export const fuelIntegrationsApi = {
  /** Inbound provider webhook health — shared inbox, not filtered to fuel or to a site. */
  inboundHealth: (signal?: AbortSignal) =>
    apiClient.get<IntegrationHealth>(`${BASE}/integrations/health`, undefined, signal),

  /** Outbound publication health. Needs `FUEL_INTEGRATION_REPLAY`; it is not site-scoped. */
  outboxHealth: (signal?: AbortSignal) =>
    apiClient.get<OutboxHealth>(`${BASE}/integrations/outbox/health`, undefined, signal),

  replay: (messageId: string) =>
    apiClient.post<ReplayResult>(`${BASE}/integrations/outbox/${messageId}/replay`, undefined, {
      idempotent: false,
    }),
};

export const fuelDashboardApi = {
  snapshot: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<FuelDashboardSnapshot>(`${BASE}/dashboard`, { siteCode }, signal),
};
