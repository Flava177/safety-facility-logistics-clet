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
  FuelDashboardSnapshot,
  FuelPolicy,
  FuelTransaction,
  ImportResult,
  IntegrationHealth,
  LogbookSearchParams,
  LogbookTransitionRequest,
  OutboxHealth,
  ReplayResult,
  TransactionSearchParams,
  VoidTransactionRequest,
} from './dto';

/**
 * Typed client for the S168 fuel API.
 *
 * Paths were taken from the controllers and confirmed against the running service's `/v3/api-docs`,
 * not from `docs/fuel/S168_Fuel_API_Inventory.md` — that document lists `GET /policies/{id}`,
 * `GET /imports/{id}` and a `/reconciliations` pair that do not exist. The differences are recorded
 * in `docs/fuel/S168_Fuel_Frontend_Gap_Register.md`. Nothing here calls an endpoint that is not
 * there, and nothing here invents a response shape.
 *
 * Two service-wide facts shape this file. **`siteCode` is required** on every collection and on the
 * dashboard, so no query is site-optional. And **there is no pagination** — each collection takes a
 * `size` limit and returns a bare array, which is why `DEFAULT_WINDOW` exists and why the registers
 * page over what came back rather than over the register.
 */

const BASE = '/api/v1/fuel';

/**
 * How many records the console asks for.
 *
 * The service defaults to 100 and imposes no ceiling of its own. 200 is a compromise: wide enough
 * that a day's transactions at one site fit inside one window, small enough that the response stays
 * quick. When the service returns exactly this many, every register warns that the window is full.
 */
export const DEFAULT_WINDOW = 200;

const asQuery = (params: object | undefined): QueryParams | undefined =>
  params as QueryParams | undefined;

export const fuelPoliciesApi = {
  /** `GET /policies?siteCode=` — the whole register for a site; there is no paging or filtering. */
  list: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<FuelPolicy[]>(`${BASE}/policies`, { siteCode }, signal),

  create: (body: CreatePolicyRequest) => apiClient.post<FuelPolicy>(`${BASE}/policies`, body),
};

export const fuelTransactionsApi = {
  search: (params: TransactionSearchParams, signal?: AbortSignal) =>
    apiClient.get<FuelTransaction[]>(
      `${BASE}/transactions`,
      asQuery({ size: DEFAULT_WINDOW, ...params }),
      signal,
    ),

  findById: (transactionId: string, signal?: AbortSignal) =>
    apiClient.get<FuelTransaction>(`${BASE}/transactions/${transactionId}`, undefined, signal),

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
   * `GET /reports/transactions.csv` — a `text/csv` download, capped at 500 rows service-side.
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
    apiClient.get<DriverLogbook[]>(
      `${BASE}/logbooks`,
      asQuery({ size: DEFAULT_WINDOW, ...params }),
      signal,
    ),

  findById: (logbookId: string, signal?: AbortSignal) =>
    apiClient.get<DriverLogbook>(`${BASE}/logbooks/${logbookId}`, undefined, signal),

  create: (body: CreateLogbookRequest) => apiClient.post<DriverLogbook>(`${BASE}/logbooks`, body),

  /**
   * One path serves all six transitions (`{action:submit|review|return|approve|reopen|cancel}`).
   *
   * `comment` is the single free-text field the service takes; which of the domain's three
   * fields it lands in depends on the action — `reviewComment` for return and approve,
   * `transitionReason` for reopen and cancel.
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
    apiClient.get<FuelAnomalyCase[]>(
      `${BASE}/anomalies`,
      asQuery({ size: DEFAULT_WINDOW, ...params }),
      signal,
    ),

  findById: (anomalyId: string, signal?: AbortSignal) =>
    apiClient.get<FuelAnomalyCase>(`${BASE}/anomalies/${anomalyId}`, undefined, signal),

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
   * The result is the only view of the batch there will ever be: `fuel_import_batches` and
   * `fuel_import_rows` are written but no endpoint reads them (gap 2).
   */
  uploadCsv: (siteCode: string, sourceSystem: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postForm<ImportResult>(`${BASE}/imports/csv`, form, {
      query: { siteCode, sourceSystem },
    });
  },
};

export const fuelIntegrationsApi = {
  /** Inbound provider webhook health — shared inbox, filtered to nothing in particular. */
  inboundHealth: (signal?: AbortSignal) =>
    apiClient.get<IntegrationHealth>(`${BASE}/integrations/health`, undefined, signal),

  /** Outbound publication health. Needs `FUEL_INTEGRATION_REPLAY`; it is not site-scoped. */
  outboxHealth: (signal?: AbortSignal) =>
    apiClient.get<OutboxHealth>(`${BASE}/integrations/outbox/health`, undefined, signal),

  replay: (messageId: string) =>
    apiClient.post<ReplayResult>(
      `${BASE}/integrations/outbox/${messageId}/replay`,
      undefined,
      { idempotent: false },
    ),
};

export const fuelDashboardApi = {
  snapshot: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<FuelDashboardSnapshot>(`${BASE}/dashboard`, { siteCode }, signal),
};
