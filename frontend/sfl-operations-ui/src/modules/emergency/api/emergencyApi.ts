import { apiClient, downloadFile } from 'shared/api/client';
import type {
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
  DrillRun,
  EmergencyDashboard,
  EmergencyOutboxHealth,
  EmergencyScenario,
  NotificationActivation,
  NotificationTemplate,
  RecipientZone,
  StartDrillRequest,
} from './dto';
import type { ActivationStatus } from './enums';

/**
 * The typed client for the S174 emergency notification service.
 *
 * Every call names `emergency` as its service, because this module is the only part of the
 * dashboard that does not talk to `sfl-fleet-logistics-service`. Nothing else differs: the same
 * actor headers, the same correlation id, the same `Idempotency-Key` on creates and the same
 * `ApiResponse` envelope.
 *
 * Thirty operations exist on the service. Twenty-eight are here. The two that are not are the
 * provider callbacks — `POST /provider-callbacks/{provider}/delivery-status` and
 * `/acknowledgements` — which require an HMAC signature over the raw body and a registered shared
 * secret. A browser cannot hold that secret, and a dashboard that posted delivery facts would be
 * fabricating them. They belong to the provider and are left to it.
 */

const BASE = '/api/v1/emergency';

/**
 * What the service returns before it truncates.
 *
 * There is no `size` parameter on any S174 collection: the limit is fixed in the application
 * service (200 activations, 500 rows in the CSV export) and cannot be raised from here. So this is
 * not a request — it is the number the screens compare a full-looking response against. Recorded as
 * gap 1.
 */
export const ACTIVATION_WINDOW = 200;

/** Records — templates, scenarios, audience groups and recipient zones (SRS-SFL-S174-01). */
export const emergencyRecordsApi = {
  templates: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<NotificationTemplate[]>(`${BASE}/templates`, { siteCode }, signal, 'emergency'),

  template: (id: string, signal?: AbortSignal) =>
    apiClient.get<NotificationTemplate>(`${BASE}/templates/${id}`, undefined, signal, 'emergency'),

  createTemplate: (body: CreateTemplateRequest) =>
    apiClient.post<NotificationTemplate>(`${BASE}/templates`, body, { service: 'emergency' }),

  scenarios: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<EmergencyScenario[]>(`${BASE}/scenarios`, { siteCode }, signal, 'emergency'),

  createScenario: (body: CreateScenarioRequest) =>
    apiClient.post<EmergencyScenario>(`${BASE}/scenarios`, body, { service: 'emergency' }),

  audienceGroups: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<AudienceGroup[]>(`${BASE}/audience-groups`, { siteCode }, signal, 'emergency'),

  createAudienceGroup: (body: CreateAudienceGroupRequest) =>
    apiClient.post<AudienceGroup>(`${BASE}/audience-groups`, body, { service: 'emergency' }),

  recipientZones: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<RecipientZone[]>(`${BASE}/recipient-zones`, { siteCode }, signal, 'emergency'),

  createRecipientZone: (body: CreateRecipientZoneRequest) =>
    apiClient.post<RecipientZone>(`${BASE}/recipient-zones`, body, { service: 'emergency' }),
};

/** Activations — the approval-gated workflow and its terminal states (SRS-SFL-S174-02). */
export const activationsApi = {
  search: (
    query: { siteCode: string; status?: ActivationStatus },
    signal?: AbortSignal,
  ) =>
    apiClient.get<NotificationActivation[]>(
      `${BASE}/activations`,
      { siteCode: query.siteCode, status: query.status },
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

  /** The send. Fans out to every selected channel and stamps the fast-lane elapsed time. */
  activate: (id: string) =>
    apiClient.post<NotificationActivation>(`${BASE}/activations/${id}/activate`, undefined, {
      service: 'emergency',
      idempotent: false,
    }),

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
  search: (siteCode: string, signal?: AbortSignal) =>
    apiClient.get<DrillRun[]>(`${BASE}/drills`, { siteCode }, signal, 'emergency'),

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
