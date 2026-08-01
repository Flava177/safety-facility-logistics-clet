/**
 * S159 closed sets, transcribed from the service enums.
 *
 * Transcribed rather than fetched, like every other module's enums, because a filter dropdown that
 * waits on a round trip is worse than one that is occasionally a release behind — and the service
 * refuses an unknown value anyway.
 */

export const BOOKING_STATUSES = [
  'REQUESTED',
  'CONFIRMED',
  'IN_USE',
  'COMPLETED',
  'REJECTED',
  'CANCELLED',
  'NO_SHOW',
] as const;
export type BookingStatus = (typeof BOOKING_STATUSES)[number];

/** The four states nothing moves out of, from `BookingStatus.TRANSITIONS`. */
export const TERMINAL_STATUSES: BookingStatus[] = [
  'COMPLETED',
  'REJECTED',
  'CANCELLED',
  'NO_SHOW',
];

export const BOOKING_PURPOSES = [
  'LECTURE',
  'MOOT',
  'EXAMINATION',
  'MEETING',
  'EVENT',
  'RESERVED',
  'OTHER',
] as const;
export type BookingPurpose = (typeof BOOKING_PURPOSES)[number];

export const READINESS_HOLD_REASONS = [
  'SPACE_BLOCKED',
  'NOT_EXAMINATION_READY',
  'LOCKED_FOR_EXAMINATION',
  'SPACE_WITHDRAWN',
] as const;
export type ReadinessHoldReason = (typeof READINESS_HOLD_REASONS)[number];

export const RESOURCE_CATEGORIES = [
  'PROJECTOR',
  'AUDIO_VISUAL',
  'PUBLIC_ADDRESS',
  'FURNITURE_SET',
  'COMPUTING',
  'RECORDING',
  'CATERING',
  'OTHER',
] as const;
export type ResourceCategory = (typeof RESOURCE_CATEGORIES)[number];

/**
 * Three states, because "nobody got to it" is not "done".
 *
 * `SKIPPED` is a decision somebody took and had to justify; `PENDING` past its due time is a lapse.
 * Collapsing them would lose the only distinction the turnaround queue exists to make.
 */
export const SETUP_TASK_STATUSES = ['PENDING', 'DONE', 'SKIPPED'] as const;
export type SetupTaskStatus = (typeof SETUP_TASK_STATUSES)[number];

/** What a setup task may be resolved to. `PENDING` is the state it starts in, not an outcome. */
export const SETUP_TASK_OUTCOMES: SetupTaskStatus[] = ['DONE', 'SKIPPED'];

export const APPROVAL_DECISIONS = ['APPROVED', 'REJECTED'] as const;
export type ApprovalDecision = (typeof APPROVAL_DECISIONS)[number];

/** What a readiness hold means to somebody who has the hall in their diary. */
export const HOLD_REASON_DESCRIPTIONS: Record<ReadinessHoldReason, string> = {
  SPACE_BLOCKED: 'A critical readiness blocker is open on this space.',
  NOT_EXAMINATION_READY: 'The space is degraded, and this booking is an examination.',
  LOCKED_FOR_EXAMINATION: 'The space is locked for examination use, and this booking is not one.',
  SPACE_WITHDRAWN: 'The space has left active service — suspended or archived.',
};
