/**
 * S174 enum values, transcribed from the emergency notification service's domain model.
 *
 * Every list here is the full set of constants the service will accept, in the order the domain
 * declares them. They drive the selects, so an operator can never submit a value the service will
 * reject with "No enum constant …" — the failure mode a free-text field produces.
 *
 * Where a set is deliberately narrower than the domain's, the comment says why, because a missing
 * option is otherwise indistinguishable from an oversight.
 */

/** `ChannelType` — how a broadcast reaches people. SFL governs; vendor gateways deliver. */
export const CHANNEL_TYPES = ['SMS', 'EMAIL', 'PUSH', 'VOICE', 'SIREN', 'DIGITAL_SIGNAGE'] as const;
export type ChannelType = (typeof CHANNEL_TYPES)[number];

/** `Priority` — the severity band, which drives the acknowledgement SLA. */
export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type Priority = (typeof PRIORITIES)[number];

/** `NotificationActivation.Mode`. */
export const ACTIVATION_MODES = ['ROUTINE', 'BREAK_GLASS', 'DEGRADED'] as const;
export type ActivationMode = (typeof ACTIVATION_MODES)[number];

/** `NotificationActivation.Status` — every state the aggregate can hold. */
export const ACTIVATION_STATUSES = [
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'ACTIVATING',
  'ACTIVE',
  'BREAK_GLASS_ACTIVE',
  'PARTIALLY_DELIVERED',
  'ESCALATED',
  'ALL_CLEAR_PENDING',
  'CLOSED',
  'CANCELLED',
  'FAILED',
  'REOPENED',
] as const;
export type ActivationStatus = (typeof ACTIVATION_STATUSES)[number];

/**
 * The statuses an activation can actually be *put into* through this dashboard.
 *
 * `ACTIVATING` and `FAILED` have no transition that produces them and no endpoint that sets them;
 * `PARTIALLY_DELIVERED` is set by provider delivery callbacks; `ESCALATED` only by the scheduled
 * acknowledgement sweep; `CANCELLED` and `REOPENED` exist on the domain record but are not exposed
 * by any controller. They stay in `ACTIVATION_STATUSES` because a stored record may hold them and
 * the filter must be able to find it — see gaps 1 and 2 in the register.
 */
export const OPERATOR_REACHABLE_STATUSES: readonly ActivationStatus[] = [
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'ACTIVE',
  'BREAK_GLASS_ACTIVE',
  'ALL_CLEAR_PENDING',
  'CLOSED',
];

/** `ChannelStatus` — the aggregate standing of one channel of one activation. */
export const CHANNEL_STATUSES = [
  'PENDING',
  'SENDING',
  'DELIVERED',
  'PARTIALLY_DELIVERED',
  'FAILED',
] as const;
export type ChannelStatus = (typeof CHANNEL_STATUSES)[number];

/** `DeliveryStatus` — a per-recipient outcome reported by a provider callback. */
export const DELIVERY_STATUSES = ['QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'EXPIRED'] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

/** `RetentionClass` — mandatory on closure evidence (SRS-SFL-S174-03). */
export const RETENTION_CLASSES = [
  'OPERATIONAL_1_YEAR',
  'COMPLIANCE_7_YEARS',
  'INCIDENT_10_YEARS',
  'LEGAL_HOLD',
] as const;
export type RetentionClass = (typeof RETENTION_CLASSES)[number];

/** `RecordLifecycle` — master-data standing for templates, scenarios, audiences and zones. */
export const RECORD_LIFECYCLES = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;
export type RecordLifecycle = (typeof RECORD_LIFECYCLES)[number];

/** `DrillRun.Status`. `CANCELLED` has no endpoint — a started drill can only be completed. */
export const DRILL_STATUSES = ['RUNNING', 'COMPLETED', 'CANCELLED'] as const;
export type DrillStatus = (typeof DRILL_STATUSES)[number];

/**
 * What each retention class means, in the words an operator needs to choose between them.
 *
 * The service will accept any of the four for any closure; which one is correct is a records
 * decision, not a technical one, so the screen states the consequence rather than guessing.
 */
export const RETENTION_DESCRIPTIONS: Record<RetentionClass, string> = {
  OPERATIONAL_1_YEAR: 'Routine operational record. Disposed after one year.',
  COMPLIANCE_7_YEARS: 'Held for regulatory compliance. Disposed after seven years.',
  INCIDENT_10_YEARS: 'Incident record. Held ten years — the default for an emergency closure.',
  LEGAL_HOLD: 'Held indefinitely for legal proceedings. Never disposed automatically.',
};

/** What each channel actually does, so a channel is chosen for a reason rather than by habit. */
export const CHANNEL_DESCRIPTIONS: Record<ChannelType, string> = {
  SMS: 'Text to every mobile number in the audience.',
  EMAIL: 'Message to every directory address in the audience.',
  PUSH: 'Notification to the SFL mobile application.',
  VOICE: 'Automated call, read from the template body.',
  SIREN: 'Site siren. SFL records the request; certified life-safety hardware is never actuated.',
  DIGITAL_SIGNAGE: 'Message posted to site display boards.',
};

/** The priority bands, stated as what they commit the responders to. */
export const PRIORITY_DESCRIPTIONS: Record<Priority, string> = {
  LOW: 'Informational. No acknowledgement is chased.',
  MEDIUM: 'Routine operational notice.',
  HIGH: 'Requires attention. Unacknowledged recipients are escalated.',
  CRITICAL: 'Life safety. The shortest acknowledgement window applies.',
};
