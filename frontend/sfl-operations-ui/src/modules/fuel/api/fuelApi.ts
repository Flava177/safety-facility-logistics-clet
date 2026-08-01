import { apiClient, downloadFile } from 'shared/api/client';
import { QueryParams } from 'shared/api/types';
import {
  AnomalyActionRequest,
  AnomalySearchParams,
  CardSearchParams,
  CaptureTransactionRequest,
  CreateLogbookRequest,
  CreatePolicyRequest,
  DriverLogbook,
  FuelCard,
  FuelCardTransitionRequest,
  FuelAnomalyCase,
  FuelAuditEvent,
  DailyFuelTotals,
  FuelDashboardSnapshot,
  FuelImportBatch,
  FuelPageResponse,
  FuelImportRow,
  FuelPolicy,
  FuelReconciliation,
  FuelTransaction,
  ImportResult,
  ImportRowSearchParams,
  ImportSearchParams,
  IntegrationHealth,
  IssueFuelCardRequest,
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

/** Fuel card register. The wire payload is masked only — no full payment-card number is accepted. */
export const fuelCardsApi = {
  search: (params: CardSearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelCard>>(
      `${BASE}/cards`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (cardId: string, signal?: AbortSignal) =>
    apiClient.get<FuelCard>(`${BASE}/cards/${cardId}`, undefined, signal),

  issue: (body: IssueFuelCardRequest) => apiClient.post<FuelCard>(`${BASE}/cards`, body),

  transition: (
    cardId: string,
    action: 'assign' | 'suspend' | 'reinstate' | 'cancel',
    body: FuelCardTransitionRequest = {},
  ) =>
    apiClient.post<FuelCard>(`${BASE}/cards/${cardId}/${action}`, body, {
      idempotent: false,
    }),
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

  /**
   * One batch header.
   *
   * The response also carries every row, which is why {@link rows} exists — a file of thousands made
   * the detail read unusable, and the screen filtered those rows in the browser.
   */
  findById: (batchId: string, signal?: AbortSignal) =>
    apiClient.get<FuelImportBatch>(`${BASE}/imports/${batchId}`, undefined, signal),

  /**
   * The batch's rows, paged and filtered by the service.
   *
   * `status` matters more than the paging does. The rejected rows are the only view of an import
   * anybody needs, and filtering them out of a page would have found the rejections that happened to
   * land on the page being looked at rather than the ones in the file.
   */
  rows: (batchId: string, params: ImportRowSearchParams = {}, signal?: AbortSignal) =>
    apiClient.get<FuelPageResponse<FuelImportRow>>(
      `${BASE}/imports/${batchId}/rows`,
      asQuery({ size: 50, ...params }),
      signal,
    ),
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

  /**
   * Spend and volume by day, aggregated by the service.
   *
   * The spend chart used to bucket a page of transactions in the browser, so it described that page
   * rather than the site — and quietly under-reported the moment a busy fortnight exceeded one page.
   */
  dailyTotals: (siteCode: string, from: string, to: string, signal?: AbortSignal) =>
    apiClient.get<DailyFuelTotals[]>(
      `${BASE}/dashboard/daily-totals`,
      { siteCode, from, to },
      signal,
    ),

  /**
   * Open anomaly cases counted by type, across the whole site.
   *
   * Open means anything not closed or cancelled, which is the same definition the queue uses.
   */
  anomalyCounts: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<Record<string, number>>(
      `${BASE}/dashboard/anomaly-counts`,
      { siteCode },
      signal,
    ),
};
