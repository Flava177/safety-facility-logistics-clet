import type { SiteCodeValue } from 'modules/fuel/api/dto';
import type {
  ActivationMode,
  ActivationStatus,
  ChannelStatus,
  ChannelType,
  DeliveryStatus,
  DrillStatus,
  Priority,
  RecordLifecycle,
  RetentionClass,
} from './enums';

/**
 * Wire types for the S174 emergency notification service.
 *
 * These are the domain records as they serialise — the controllers return the aggregates directly,
 * so `SiteCode` arrives as `{ value }` rather than as a string and the provenance block is nested
 * on every record.
 *
 * `SiteCodeValue` is re-used from the fuel module because it is genuinely the same shape. The
 * provenance block is **not**, and the difference is easy to miss: the fleet-logistics services
 * name their correlation field `auditCorrelationId`, this one names it `correlationId`, and every
 * other field here is non-null where fleet's are optional. Sharing the type would have compiled and
 * then rendered an empty correlation id on every emergency screen.
 */
export type { SiteCodeValue };

/** `RecordMetadata` — the provenance block S174 carries on every aggregate. */
export interface RecordMetadata {
  createdBy: string;
  createdAt: string;
  lastModifiedBy: string;
  lastModifiedAt: string;
  version: number;
  sourceChannel: string;
  /** Named `correlationId` here, not `auditCorrelationId` as the fleet-logistics services do. */
  correlationId: string | null;
}

/** `NotificationTemplate` — reusable message text bound to a set of channels. */
export interface NotificationTemplate {
  id: string;
  templateCode: string;
  siteCode: SiteCodeValue;
  title: string;
  body: string;
  channels: ChannelType[];
  /** Marks the template a pre-authorised role may fire without per-message approval (Arch §0E). */
  breakGlassEligible: boolean;
  lifecycle: RecordLifecycle;
  metadata: RecordMetadata;
}

/** `EmergencyScenario` — a declared emergency type with a default template and a priority. */
export interface EmergencyScenario {
  id: string;
  scenarioCode: string;
  siteCode: SiteCodeValue;
  name: string;
  priority: Priority;
  defaultTemplateId: string | null;
  breakGlassEligible: boolean;
  lifecycle: RecordLifecycle;
  metadata: RecordMetadata;
}

/**
 * `AudienceGroup` — a named set of recipients.
 *
 * Contact detail is held by the directory and never reaches this dashboard; `directoryReference`
 * is the pointer and `recipientCount` is the sizing every delivery and acknowledgement figure is
 * reconciled against.
 */
export interface AudienceGroup {
  id: string;
  groupCode: string;
  siteCode: SiteCodeValue;
  name: string;
  directoryReference: string | null;
  recipientCount: number;
  lifecycle: RecordLifecycle;
  metadata: RecordMetadata;
}

/** `RecipientZone` — a building, floor or room scope, referenced into the facilities model. */
export interface RecipientZone {
  id: string;
  zoneCode: string;
  siteCode: SiteCodeValue;
  name: string;
  locationReference: string | null;
  lifecycle: RecordLifecycle;
  metadata: RecordMetadata;
}

/** `NotificationActivation` — the workflow aggregate. */
export interface NotificationActivation {
  id: string;
  activationNumber: string;
  siteCode: SiteCodeValue;
  scenarioId: string | null;
  templateId: string | null;
  audienceGroupIds: string[];
  recipientZoneIds: string[];
  channels: ChannelType[];
  mode: ActivationMode;
  status: ActivationStatus;
  priority: Priority;
  incidentReference: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  afterActionApprovedBy: string | null;
  afterActionApprovedAt: string | null;
  afterActionJustification: string | null;
  allClearAt: string | null;
  closureReason: string | null;
  /** Set by the service at closure — "channels=3; sent=720; delivered=0; failed=0". */
  deliverySummary: string | null;
  acknowledgementSummary: string | null;
  closureEvidenceId: string | null;
  escalationLevel: number;
  degradedMode: boolean;
  fallbackPath: string | null;
  /** Milliseconds from the send command to the last gateway hand-off. The §0E fast-lane measure. */
  fastLaneMillis: number | null;
  metadata: RecordMetadata;
}

/** `NotificationChannel` — one activation × one channel, with its fan-out counters. */
export interface NotificationChannel {
  id: string;
  activationId: string;
  siteCode: SiteCodeValue;
  channelType: ChannelType;
  status: ChannelStatus;
  targetCount: number;
  sentCount: number;
  deliveredCount: number;
  failedCount: number;
  acknowledgedCount: number;
  metadata: RecordMetadata;
}

/** `ActivationService.ActivationStatusView` — the activation with its fan-out and ack count. */
export interface ActivationStatusView {
  activation: NotificationActivation;
  channels: NotificationChannel[];
  acknowledgements: number;
}

/** `DrillRun` — a rehearsal of the activation path, with its performance figures. */
export interface DrillRun {
  id: string;
  drillNumber: string;
  siteCode: SiteCodeValue;
  scenarioId: string | null;
  status: DrillStatus;
  targetRecipients: number;
  reachedRecipients: number;
  acknowledgedRecipients: number;
  activationMillis: number | null;
  startedAt: string;
  completedAt: string | null;
  notes: string | null;
  metadata: RecordMetadata;
}

/**
 * `GET /dashboard` — the service's own counts plus its freshness verdict.
 *
 * `stale` is decided by the service against a per-site configured threshold, not by this
 * dashboard, so the screen reports it rather than recomputing it.
 */
export interface EmergencyDashboard {
  activeActivationCount: number;
  breakGlassCount: number;
  failedRecipientCount: number;
  ackPendingCount: number;
  escalatedCount: number;
  allClearPendingCount: number;
  drillCount: number;
  sourceUpdatedAt: string | null;
  stale: boolean;
  generatedAt: string;
}

/** `OutboxAdminPort.OutboxEntry`. */
export interface EmergencyOutboxEntry {
  id: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  status: string;
  attemptCount: number;
  failureReason: string | null;
  createdAt: string;
}

/** `OutboxAdminPort.OutboxHealth` — outbound only; S174 publishes no inbound inbox read. */
export interface EmergencyOutboxHealth {
  pending: number;
  published: number;
  deadLettered: number;
  recentDeadLetters: EmergencyOutboxEntry[];
}

// ---- request bodies ---------------------------------------------------------------------------

export interface CreateActivationRequest {
  siteCode: string;
  scenarioId?: string | null;
  templateId?: string | null;
  audienceGroupIds?: string[];
  recipientZoneIds?: string[];
  channels?: ChannelType[];
  priority?: Priority | null;
  incidentReference?: string | null;
}

/** Break-glass tightens two fields the routine create leaves optional: template and channels. */
export interface BreakGlassRequest extends CreateActivationRequest {
  templateId: string;
  channels: ChannelType[];
}

export interface CloseActivationRequest {
  reason: string;
  evidenceFileName?: string | null;
  evidenceContentType?: string | null;
  evidenceStorageReference: string;
  evidenceSha256?: string | null;
  retentionClass?: RetentionClass | null;
}

export interface CreateTemplateRequest {
  siteCode: string;
  templateCode?: string | null;
  title: string;
  body: string;
  channels: ChannelType[];
  breakGlassEligible: boolean;
}

export interface CreateScenarioRequest {
  siteCode: string;
  scenarioCode?: string | null;
  name: string;
  priority: Priority;
  defaultTemplateId?: string | null;
  breakGlassEligible: boolean;
}

export interface CreateAudienceGroupRequest {
  siteCode: string;
  groupCode?: string | null;
  name: string;
  directoryReference?: string | null;
  recipientCount: number;
}

export interface CreateRecipientZoneRequest {
  siteCode: string;
  zoneCode?: string | null;
  name: string;
  locationReference?: string | null;
}

export interface StartDrillRequest {
  siteCode: string;
  scenarioId?: string | null;
  targetRecipients: number;
  notes?: string | null;
}

export interface CompleteDrillRequest {
  reachedRecipients: number;
  acknowledgedRecipients: number;
  activationMillis: number;
  notes?: string | null;
}

/**
 * `EmergencyPageResponse<T>` — the envelope every S174 collection now returns.
 *
 * Identical in shape to the fleet, fuel and dispatch ones. Before the gap-closure round these
 * endpoints returned a bare array capped at 200 by the application service, with no `size`
 * parameter to raise it — which is why the register paged a window client-side and warned when it
 * came back full. Both are gone.
 */
export interface EmergencyPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort: string | null;
}

export interface PagingParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface RecordSearchParams extends PagingParams {
  siteCode: string;
  /** Contains-match. Over title, code and body for templates; over name and code for the rest. */
  search?: string;
  lifecycle?: RecordLifecycle | '';
  breakGlassEligible?: boolean;
}

/** Everything the activation register can now ask the service for. That was gap 2. */
export interface ActivationSearchParams extends PagingParams {
  siteCode: string;
  status?: ActivationStatus | '';
  mode?: ActivationMode | '';
  priority?: Priority | '';
  /** Contains-match over the incident reference and the activation number. */
  incidentReference?: string;
  /** `NotificationActivation.open()` — not closed, cancelled or rejected. */
  openOnly?: boolean;
  /** `NotificationActivation.active()` — a broadcast is out and has not been stood down. */
  liveOnly?: boolean;
  /** Break-glass sends nobody has accounted for yet. The one figure an auditor asks about. */
  afterActionOutstanding?: boolean;
  scenarioId?: string;
  templateId?: string;
  from?: string;
  to?: string;
}

export interface DrillSearchParams extends PagingParams {
  siteCode: string;
  status?: DrillStatus | '';
  scenarioId?: string;
  from?: string;
  to?: string;
}

/** One recorded transition, from `activation_history`. */
export interface ActivationHistoryEntry {
  id: string;
  activationId: string;
  fromStatus: string | null;
  toStatus: string;
  action: string;
  actor: string | null;
  comment: string | null;
  occurredAt: string;
  correlationId: string | null;
}

/** Every provider fact recorded against one activation. Closed gap 8. */
export interface ActivationDeliveryDetail {
  receipts: DeliveryReceiptRecord[];
  acknowledgements: AcknowledgementRecord[];
}

export interface DeliveryReceiptRecord {
  id: string;
  activationId: string;
  siteCode: SiteCodeValue;
  channelType: ChannelType;
  provider: string;
  providerMessageId: string;
  recipientRef: string | null;
  status: DeliveryStatus;
  /** The provider's own words for why it failed — what makes a failed recipient chaseable. */
  reason: string | null;
  occurredAt: string;
  createdBy: string;
  createdAt: string;
  sourceChannel: string;
  correlationId: string | null;
}

export interface AcknowledgementRecord {
  id: string;
  activationId: string;
  siteCode: SiteCodeValue;
  channelType: ChannelType | null;
  recipientRef: string;
  acknowledgedAt: string;
  createdBy: string;
  createdAt: string;
  sourceChannel: string;
  correlationId: string | null;
}

/**
 * `InboxAdminPort.InboxHealth` — the inbound provider feed. Closed gap 3.
 *
 * Read-only by design: a rejected inbound message failed signature or schema validation, so the
 * sending system has to correct and re-send it. Only dead-lettered outbound messages are replayable.
 */
export interface EmergencyInboxHealth {
  processed: number;
  rejected: number;
  deadLettered: number;
  recentMessages: EmergencyInboxMessage[];
  checkedAt: string;
}

export interface EmergencyInboxMessage {
  id: string;
  sourceSystem: string;
  eventType: string;
  siteScope: string | null;
  status: string;
  attempts: number;
  failureReason: string | null;
  idempotencyKey: string | null;
  receivedAt: string;
  processedAt: string | null;
}
