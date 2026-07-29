import type { SiteCodeValue } from 'modules/fuel/api/dto';
import type {
  ActivationMode,
  ActivationStatus,
  ChannelStatus,
  ChannelType,
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
