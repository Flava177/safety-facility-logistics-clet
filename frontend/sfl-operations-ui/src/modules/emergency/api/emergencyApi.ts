import { apiClient, downloadFile } from 'shared/api/client';
import type { QueryParams } from 'shared/api/types';
import type {
  ActivationDeliveryDetail,
  ActivationHistoryEntry,
  ActivationReasonRequest,
  ActivationSearchParams,
  ActivationStatusView,
  AudienceGroup,
  BreakGlassRequest,
  CloseActivationRequest,
  CompleteDrillRequest,
  CreateActivationRequest,
  CreateAudienceGroupRequest,
  CreateRecipientZoneRequest,
  CreateScenarioRequest,
  CreateTemplateRequest,
  DegradedFallbackRequest,
  DrillRun,
  DrillSearchParams,
  EmergencyDashboard,
  EmergencyInboxHealth,
  EmergencyPageResponse,
  EmergencyOutboxHealth,
  EmergencyScenario,
  NotificationActivation,
  NotificationTemplate,
  RecipientZone,
  RecordSearchParams,
  StartDrillRequest,
} from './dto';
import type { RecordLifecycle } from './enums';

/**
 * The typed client for the S174 emergency notification service.
 *
 * Every call names `emergency` as its service, because this module is the only part of the
 * dashboard that does not talk to `sfl-fleet-logistics-service`. Nothing else differs: the same
 * actor headers, the same correlation id, the same `Idempotency-Key` on creates and the same
 * `ApiResponse` envelope.
 *
 * S174 operator and integration operations exposed by the service are gathered here. The real
 * provider callbacks — `POST /provider-callbacks/{provider}/delivery-status` and
 * `/acknowledgements` — which require an HMAC signature over the raw body and a registered shared
 * secret. A browser cannot hold that secret, and a dashboard that posted delivery facts would be
 * fabricating them. They belong to the provider and are left to it.
 */

const BASE = '/api/v1/emergency';

const asQuery = (params: object | undefined): QueryParams | undefined =>
  params as QueryParams | undefined;

/** Default page size. The service clamps anything above 200. */
export const DEFAULT_PAGE_SIZE = 25;

/** Records — templates, scenarios, audience groups and recipient zones (SRS-SFL-S174-01). */
export const emergencyRecordsApi = {
  templates: (query: RecordSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<NotificationTemplate>>(
      `${BASE}/templates`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  template: (id: string, signal?: AbortSignal) =>
    apiClient.get<NotificationTemplate>(`${BASE}/templates/${id}`, undefined, signal, 'emergency'),

  createTemplate: (body: CreateTemplateRequest) =>
    apiClient.post<NotificationTemplate>(`${BASE}/templates`, body, { service: 'emergency' }),

  scenarios: (query: RecordSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<EmergencyScenario>>(
      `${BASE}/scenarios`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  createScenario: (body: CreateScenarioRequest) =>
    apiClient.post<EmergencyScenario>(`${BASE}/scenarios`, body, { service: 'emergency' }),

  audienceGroups: (query: RecordSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<AudienceGroup>>(
      `${BASE}/audience-groups`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  createAudienceGroup: (body: CreateAudienceGroupRequest) =>
    apiClient.post<AudienceGroup>(`${BASE}/audience-groups`, body, { service: 'emergency' }),

  recipientZones: (query: RecordSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<RecipientZone>>(
      `${BASE}/recipient-zones`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  createRecipientZone: (body: CreateRecipientZoneRequest) =>
    apiClient.post<RecipientZone>(`${BASE}/recipient-zones`, body, { service: 'emergency' }),

  scenario: (id: string, signal?: AbortSignal) =>
    apiClient.get<EmergencyScenario>(`${BASE}/scenarios/${id}`, undefined, signal, 'emergency'),

  audienceGroup: (id: string, signal?: AbortSignal) =>
    apiClient.get<AudienceGroup>(`${BASE}/audience-groups/${id}`, undefined, signal, 'emergency'),

  recipientZone: (id: string, signal?: AbortSignal) =>
    apiClient.get<RecipientZone>(`${BASE}/recipient-zones/${id}`, undefined, signal, 'emergency'),

  /**
   * Corrects an audience group's size and directory pointer.
   *
   * The sharp edge in gap 6: `recipientCount` is what the service fans out to and the denominator
   * every delivery percentage is read against, and it could not be corrected — a group sized at
   * zero sent to nobody and reported a completely successful broadcast. The name is deliberately
   * not editable: closed activations cite this group.
   */
  updateAudienceGroup: (id: string, body: { directoryReference?: string | null; recipientCount?: number }) =>
    apiClient.patch<AudienceGroup>(`${BASE}/audience-groups/${id}`, body, { service: 'emergency' }),

  /** Retires or reinstates a record. Archiving is not deletion — activations citing it still resolve. */
  setLifecycle: (
    resource: 'templates' | 'scenarios' | 'audience-groups' | 'recipient-zones',
    id: string,
    lifecycle: RecordLifecycle,
  ) =>
    apiClient.patch<unknown>(`${BASE}/${resource}/${id}/lifecycle`, { lifecycle }, { service: 'emergency' }),
};

/** Activations — the approval-gated workflow and its terminal states (SRS-SFL-S174-02). */
export const activationsApi = {
  search: (query: ActivationSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<NotificationActivation>>(
      `${BASE}/activations`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  detail: (id: string, signal?: AbortSignal) =>
    apiClient.get<NotificationActivation>(
      `${BASE}/activations/${id}`,
      undefined,
      signal,
      'emergency',
    ),

  /** The activation plus its per-channel fan-out and acknowledgement count, in one read. */
  status: (id: string, signal?: AbortSignal) =>
    apiClient.get<ActivationStatusView>(
      `${BASE}/activations/${id}/status`,
      undefined,
      signal,
      'emergency',
    ),

  /**
   * The activation's recorded transitions, oldest first.
   *
   * Closed gap 4. The service has written this on every state change since it was built and
   * published no way to read it, which is why the detail screen used to reconstruct a timeline from
   * whatever fields the record still carried — and silently omit any transition that left none.
   */
  history: (id: string, signal?: AbortSignal) =>
    apiClient.get<ActivationHistoryEntry[]>(
      `${BASE}/activations/${id}/history`,
      undefined,
      signal,
      'emergency',
    ),

  /** Per-recipient delivery receipts and acknowledgements. Closed gap 8. */
  delivery: (id: string, signal?: AbortSignal) =>
    apiClient.get<ActivationDeliveryDetail>(
      `${BASE}/activations/${id}/delivery`,
      undefined,
      signal,
      'emergency',
    ),

  create: (body: CreateActivationRequest) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations`, body, { service: 'emergency' }),

  submit: (id: string) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/submit`, undefined, {
      service: 'emergency',
      idempotent: false,
    }),

  approve: (id: string) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/approve`, undefined, {
      service: 'emergency',
      idempotent: false,
    }),

  reject: (id: string, reason: string) =>
    apiClient.post<NotificationActivation>(
      `${BASE}/activations/${id}/reject`,
      { reason },
      { service: 'emergency', idempotent: false },
    ),

  cancel: (id: string, body: ActivationReasonRequest) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/cancel`, body, {
      service: 'emergency',
      idempotent: false,
    }),

  /** The send. Fans out to every selected channel and stamps the fast-lane elapsed time. */
  activate: (id: string) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/activate`, undefined, {
      service: 'emergency',
      idempotent: false,
    }),

  degradedFallback: (id: string, body: DegradedFallbackRequest) =>
    apiClient.post<NotificationActivation>(
      `${BASE}/activations/${id}/degraded-fallback`,
      body,
      { service: 'emergency', idempotent: false },
    ),

  afterActionApproval: (id: string, justification: string) =>
    apiClient.post<NotificationActivation>(
      `${BASE}/activations/${id}/after-action-approval`,
      { justification },
      { service: 'emergency', idempotent: false },
    ),

  allClear: (id: string) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/all-clear`, undefined, {
      service: 'emergency',
      idempotent: false,
    }),

  close: (id: string, body: CloseActivationRequest) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/close`, body, {
      service: 'emergency',
      idempotent: false,
    }),

  reopen: (id: string, body: ActivationReasonRequest) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/reopen`, body, {
      service: 'emergency',
      idempotent: false,
    }),
};

/**
 * Break-glass — a declared-emergency send with no pre-approval (Arch §0E).
 *
 * A separate endpoint and a separate permission (`EMERGENCY_BREAK_GLASS_SEND`), not a flag on the
 * routine create. It returns an activation already in `BREAK_GLASS_ACTIVE`: there is no draft to
 * review and nothing to undo, which is exactly why the screen that calls it says so first.
 */
export const breakGlassApi = {
  send: (body: BreakGlassRequest) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/break-glass`, body, {
      service: 'emergency',
    }),
};

/** Drills — rehearsals with recorded performance (SRS-SFL-S174-05). */
export const drillsApi = {
  search: (query: DrillSearchParams, signal?: AbortSignal) =>
    apiClient.get<EmergencyPageResponse<DrillRun>>(
      `${BASE}/drills`,
      asQuery({ size: DEFAULT_PAGE_SIZE, ...query }),
      signal,
      'emergency',
    ),

  start: (body: StartDrillRequest) =>
    apiClient.post<DrillRun>(`${BASE}/drills`, body, { service: 'emergency' }),

  complete: (id: string, body: CompleteDrillRequest) =>
    apiClient.post<DrillRun>(`${BASE}/drills/${id}/complete`, body, {
      service: 'emergency',
      idempotent: false,
    }),
};

/** Dashboard and the authorised CSV export (SRS-SFL-S174-05). */
export const emergencyDashboardApi = {
  /** The same population split by status, priority, mode and channel. Closed gap 12. */
  breakdown: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<Record<string, Record<string, number>>>(
      `${BASE}/dashboard/breakdown`,
      { siteCode },
      signal,
      'emergency',
    ),

  dashboard: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<EmergencyDashboard>(`${BASE}/dashboard`, { siteCode }, signal, 'emergency'),
};

export const emergencyReportsApi = {
  /**
   * The activation register as CSV.
   *
   * Needs `EMERGENCY_REPORT_EXPORT`, which the coordinator and SOC roles do not hold — only
   * auditor, compliance officer, security director and admin do. A refusal comes back in the
   * envelope and is shown as it is written, rather than as a download that silently does nothing.
   */
  activations: (siteCode: string) =>
    downloadFile(
      `${BASE}/reports/activations.csv`,
      { siteCode },
      `emergency-activations-${siteCode}.csv`,
      'text/csv, application/json',
      'emergency',
    ),
};

/** Integration health and privileged dead-letter replay (SRS-SFL-S174-04). */
export const emergencyIntegrationsApi = {
  /**
   * The inbound provider feed.
   *
   * Closed gap 3, the most consequential gap on this service: this feed is the only thing that ever
   * writes `delivered`, `failed` and `acknowledged`, and none of it was readable. A screen showing
   * 480 sent and 0 delivered could not tell "no provider configured" from "every callback rejected
   * for a bad signature".
   */
  inbox: (recentLimit = 20, signal?: AbortSignal) =>
    apiClient.get<EmergencyInboxHealth>(
      `${BASE}/integrations/inbox`,
      { recentLimit },
      signal,
      'emergency',
    ),

  health: (signal?: AbortSignal) =>
    apiClient.get<EmergencyOutboxHealth>(
      `${BASE}/integrations/health`,
      undefined,
      signal,
      'emergency',
    ),

  replay: (messageId: string) =>
    apiClient.post<{ messageId: string; requeued: boolean }>(
      `${BASE}/integrations/outbox/${messageId}/replay`,
      undefined,
      { service: 'emergency', idempotent: false },
    ),
};
