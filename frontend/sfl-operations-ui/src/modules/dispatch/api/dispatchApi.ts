import { apiClient, downloadFile } from 'shared/api/client';
import { QueryParams } from 'shared/api/types';
import {
  AddManifestItemRequest,
  AssignTripRequest,
  CloseManifestRequest,
  ConfirmReceiptRequest,
  CourierItem,
  CreateManifestRequest,
  CustodyGaps,
  CustodyHandover,
  DispatchDashboardSnapshot,
  DispatchExceptionCase,
  DispatchIntegrationHealth,
  DispatchManifest,
  DispatchManifestItem,
  DispatchReceipt,
  DistributeInboundRequest,
  ExceptionActionRequest,
  CustodySearchParams,
  DispatchAuditEvent,
  DispatchPageResponse,
  ExceptionSearchParams,
  InboundSearchParams,
  ItemSearchParams,
  ManifestLine,
  ManifestSearchParams,
  ReceiptSearchParams,
  ScanBatchSearchParams,
  MisrouteRequest,
  ReconcileReturnRequest,
  RecordHandoverRequest,
  RegisterInboundRequest,
  RegisterItemRequest,
  ReturnReconciliation,
  ScanImportBatch,
  ScanImportRow,
  SealRequest,
} from './dto';

/**
 * Typed client for the S171 Mailroom, Courier and Dispatch Tracking API.
 *
 * Paths were taken from the controllers and confirmed against the running service's
 * `/v3/api-docs` — forty endpoints, all of them wired here, none invented.
 *
 * **Every collection is paged.** Each returns `DispatchPageResponse<T>` with `page`, `size`,
 * `totalElements` and the ordering it actually applied. That closed gap 1; the client-side window
 * and its truncation banner are gone, and so is the guesswork about whether a register was showing
 * everything.
 */

const BASE = '/api/v1/dispatch';

/** Default page size. The service clamps anything above 200. */
export const DEFAULT_PAGE_SIZE = 25;

const asQuery = (params: object | undefined): QueryParams | undefined =>
  params as QueryParams | undefined;

/** The six lifecycle moves `DispatchItemController` accepts on a courier item. */
export const ITEM_ACTIONS = [
  'stage',
  'dispatch',
  'in-transit',
  'deliver',
  'return',
  'close',
] as const;
export type ItemAction = (typeof ITEM_ACTIONS)[number];

export const courierItemsApi = {
  search: (params: ItemSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<CourierItem>>(
      `${BASE}/items`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (itemId: string, signal?: AbortSignal) =>
    apiClient.get<CourierItem>(`${BASE}/items/${itemId}`, undefined, signal),

  /** The item's transition history, read off the audit log. */
  history: (itemId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchAuditEvent[]>(`${BASE}/items/${itemId}/history`, undefined, signal),

  register: (body: RegisterItemRequest) => apiClient.post<CourierItem>(`${BASE}/items`, body),

  /**
   * One path serves all six moves. None of them takes a body — the transition is the whole request,
   * and what it is allowed to do is decided by `CourierItem`'s own state guards.
   */
  advance: (itemId: string, action: ItemAction) =>
    apiClient.post<CourierItem>(`${BASE}/items/${itemId}/${action}`, undefined, {
      idempotent: false,
    }),

  /** Re-routes a misdirected item and reassigns its handler. The reason is mandatory. */
  misroute: (itemId: string, body: MisrouteRequest) =>
    apiClient.post<CourierItem>(`${BASE}/items/${itemId}/misroute`, body, { idempotent: false }),
};

export const inboundMailApi = {
  search: (params: InboundSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<CourierItem>>(
      `${BASE}/inbound`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  /** Direction is fixed to INBOUND by the controller, so it is not part of the request. */
  register: (body: RegisterInboundRequest) => apiClient.post<CourierItem>(`${BASE}/inbound`, body),

  /**
   * Records internal distribution with an acknowledgement.
   *
   * Legal only from `RECEIVED` or `STAGED`, and the acknowledgement is what closes the item's
   * inbound obligation — the signature reference is optional but is the only evidence there is.
   */
  distribute: (itemId: string, body: DistributeInboundRequest) =>
    apiClient.post<CourierItem>(`${BASE}/inbound/${itemId}/distribute`, body, {
      idempotent: false,
    }),
};

export const manifestsApi = {
  search: (params: ManifestSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<DispatchManifest>>(
      `${BASE}/manifests`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (manifestId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchManifest>(`${BASE}/manifests/${manifestId}`, undefined, signal),

  create: (body: CreateManifestRequest) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests`, body),

  items: (manifestId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchManifestItem[]>(
      `${BASE}/manifests/${manifestId}/items`,
      undefined,
      signal,
    ),

  /**
   * The manifest's lines with each courier item resolved.
   *
   * Closed gap 8. A line carries only a `courierItemId`, so a readable manifest used to mean either
   * a fetch per line or showing an operator a bare identifier. The service resolves them in one
   * query.
   */
  lines: (manifestId: string, signal?: AbortSignal) =>
    apiClient.get<ManifestLine[]>(
      `${BASE}/manifests/${manifestId}/items`,
      { expand: 'item' },
      signal,
    ),

  /** The manifest's transition history: draft, seal, trip assignment, dispatch, transit, closure. */
  history: (manifestId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchAuditEvent[]>(`${BASE}/manifests/${manifestId}/history`, undefined, signal),

  /** Adding an item is legal only while the manifest is a draft — sealing freezes the contents. */
  addItem: (manifestId: string, body: AddManifestItemRequest) =>
    apiClient.post<DispatchManifestItem>(`${BASE}/manifests/${manifestId}/items`, body, {
      idempotent: false,
    }),

  /** Seals the manifest against its seal identifiers and item count. Draft only, and one way. */
  seal: (manifestId: string, body: SealRequest) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests/${manifestId}/seal`, body, {
      idempotent: false,
    }),

  assignTrip: (manifestId: string, body: AssignTripRequest) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests/${manifestId}/assign-trip`, body, {
      idempotent: false,
    }),

  dispatch: (manifestId: string) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests/${manifestId}/dispatch`, undefined, {
      idempotent: false,
    }),

  inTransit: (manifestId: string) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests/${manifestId}/in-transit`, undefined, {
      idempotent: false,
    }),

  /**
   * Closes the manifest. `DispatchClosurePolicy` refuses it while an exception case is open or the
   * custody chain is not closable, so the detail screen shows both before offering the action.
   */
  close: (manifestId: string, body: CloseManifestRequest) =>
    apiClient.post<DispatchManifest>(`${BASE}/manifests/${manifestId}/close`, body, {
      idempotent: false,
    }),
};

export const custodyApi = {
  /** Handovers for one consignment, in the order they were recorded. Append-only. */
  handovers: (dispatchId: string, signal?: AbortSignal) =>
    apiClient.get<CustodyHandover[]>(`${BASE}/custody`, { dispatchId }, signal),

  /** What `CustodyChainPolicy` makes of the chain: gaps, missing closure hops, and closability. */
  gaps: (dispatchId: string, signal?: AbortSignal) =>
    apiClient.get<CustodyGaps>(`${BASE}/custody/${dispatchId}/gaps`, undefined, signal),

  /**
   * Custody across a site's consignments.
   *
   * Closed gap 7. Custody was readable per consignment only, so "everything this custodian handled
   * last week" needed the manifests known first — the wrong way round when the custodian is the
   * reason for asking.
   */
  search: (params: CustodySearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<CustodyHandover>>(
      `${BASE}/custody`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  record: (body: RecordHandoverRequest) =>
    apiClient.post<CustodyHandover>(`${BASE}/custody`, body, { idempotent: false }),
};

export const receiptsApi = {
  list: (dispatchId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchReceipt[]>(`${BASE}/receipts`, { dispatchId }, signal),

  findById: (receiptId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchReceipt>(`${BASE}/receipts/${receiptId}`, undefined, signal),

  /** Receipts across a site's consignments. Closed gap 7. */
  search: (params: ReceiptSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<DispatchReceipt>>(
      `${BASE}/receipts`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  /**
   * Confirms receipt at the destination.
   *
   * The outcome is **derived**, not supplied: `ReceiptVariancePolicy` compares seal state, counts
   * and recipient against the manifest and decides CLEAN or which variance it is. Sending a
   * `captureCorrelationId` makes the confirmation idempotent, which is what lets an edge capture be
   * replayed safely once connectivity returns.
   */
  confirm: (body: ConfirmReceiptRequest) =>
    apiClient.post<DispatchReceipt>(`${BASE}/receipts`, body, { idempotent: false }),
};

export const returnsApi = {
  list: (dispatchId: string, signal?: AbortSignal) =>
    apiClient.get<ReturnReconciliation[]>(`${BASE}/returns`, { dispatchId }, signal),

  findById: (reconciliationId: string, signal?: AbortSignal) =>
    apiClient.get<ReturnReconciliation>(`${BASE}/returns/${reconciliationId}`, undefined, signal),

  /** Shortfall, extras and outcome are derived by `ReturnReconciliationPolicy` from the counts. */
  reconcile: (body: ReconcileReturnRequest) =>
    apiClient.post<ReturnReconciliation>(`${BASE}/returns/reconcile`, body, { idempotent: false }),
};

/** The thirteen transitions `DispatchExceptionController` accepts — the same set as fuel anomalies. */
export const EXCEPTION_ACTIONS = [
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
export type ExceptionAction = (typeof EXCEPTION_ACTIONS)[number];

export const dispatchExceptionsApi = {
  search: (params: ExceptionSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<DispatchExceptionCase>>(
      `${BASE}/exceptions`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  findById: (caseId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchExceptionCase>(`${BASE}/exceptions/${caseId}`, undefined, signal),

  /** The case's transition history: assignment, review, explanation, decision, escalation, closure. */
  history: (caseId: string, signal?: AbortSignal) =>
    apiClient.get<DispatchAuditEvent[]>(`${BASE}/exceptions/${caseId}/history`, undefined, signal),

  transition: (caseId: string, action: ExceptionAction, body: ExceptionActionRequest) =>
    apiClient.post<DispatchExceptionCase>(`${BASE}/exceptions/${caseId}/${action}`, body, {
      idempotent: false,
    }),
};

export const scanImportsApi = {
  /**
   * `POST /scans/imports` — multipart, two positional CSV columns.
   *
   * Column one is the row reference, column two the scanned code; a single-column file is read as
   * the code with a generated reference. Rows are classified against the manifest and land as
   * MATCHED, MISMATCH or UNREGISTERED.
   */
  upload: (
    siteCode: string,
    sourceSystem: string,
    file: File,
    options: { batchReference?: string; dispatchId?: string } = {},
  ) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.postForm<ScanImportBatch>(`${BASE}/scans/imports`, form, {
      query: { siteCode, sourceSystem, ...options },
    });
  },

  /** The site's scan batches. Closed gap 3 — a batch used to be reachable only by a kept id. */
  search: (params: ScanBatchSearchParams, signal?: AbortSignal) =>
    apiClient.get<DispatchPageResponse<ScanImportBatch>>(
      `${BASE}/scans/imports`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...params }),
      signal,
    ),

  batch: (batchId: string, signal?: AbortSignal) =>
    apiClient.get<ScanImportBatch>(`${BASE}/scans/imports/${batchId}`, undefined, signal),

  rows: (batchId: string, signal?: AbortSignal) =>
    apiClient.get<ScanImportRow[]>(`${BASE}/scans/imports/${batchId}/rows`, undefined, signal),
};

export const dispatchIntegrationsApi = {
  /** Inbound scanner and carrier health plus outbound publication, in one payload. */
  health: (signal?: AbortSignal) =>
    apiClient.get<DispatchIntegrationHealth>(`${BASE}/integrations/health`, undefined, signal),

  replay: (messageId: string) =>
    apiClient.post<Record<string, unknown>>(
      `${BASE}/integrations/outbox/${messageId}/replay`,
      undefined,
      { idempotent: false },
    ),
};

export const dispatchDashboardApi = {
  snapshot: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<DispatchDashboardSnapshot>(`${BASE}/dashboard`, { siteCode }, signal),
};

export const dispatchReportsApi = {
  /** Both reports are `text/csv` downloads; see `downloadFile` for why they are fetched, not linked. */
  items: (siteCode: string) =>
    downloadFile(`${BASE}/reports/items.csv`, { siteCode }, `dispatch-items-${siteCode}.csv`),

  exceptions: (siteCode: string) =>
    downloadFile(
      `${BASE}/reports/exceptions.csv`,
      { siteCode },
      `dispatch-exceptions-${siteCode}.csv`,
    ),
};
