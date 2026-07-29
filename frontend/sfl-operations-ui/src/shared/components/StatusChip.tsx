import { humanise } from 'modules/fleet/api/enums';
import { cn } from './cn';

export type Tone = 'ready' | 'caution' | 'blocked' | 'neutral' | 'active' | 'accent';

/**
 * A tinted pill: light surface, dark text of the same hue.
 *
 * Every pairing here is a 50/100 surface with a 700/800 label, which lands between 7:1 and 12:1 —
 * so a status is as readable as body copy (SC 1.4.3) while still being colour-coded at a glance.
 * Colour is never the only carrier: the status word is in the pill (SC 1.4.1).
 */
const toneStyles: Record<Tone, string> = {
  ready: 'bg-success-100 text-success-800',
  caution: 'bg-warning-100 text-warning-800',
  blocked: 'bg-error-100 text-error-800',
  neutral: 'bg-gray-100 text-gray-700',
  active: 'bg-teal-50 text-teal-800',
  accent: 'bg-gold-100 text-gold-900',
};

/**
 * One table maps every backend status value to a tone, so "OVERDUE" reads the same on the
 * dashboard, the register and the detail page. Anything unmapped falls back to neutral rather than
 * inventing a colour, which keeps an unknown future enum value legible instead of alarming.
 */
const statusTones: Record<string, Tone> = {
  // Readiness
  READY: 'ready',
  CONDITIONALLY_READY: 'caution',
  NOT_READY: 'blocked',

  // Vehicle lifecycle / service / availability
  ACTIVE: 'ready',
  INACTIVE: 'neutral',
  SUSPENDED: 'blocked',
  ARCHIVED: 'neutral',
  IN_SERVICE: 'ready',
  DUE: 'caution',
  OVERDUE: 'blocked',
  OUT_OF_SERVICE: 'blocked',
  AVAILABLE: 'ready',
  RESERVED: 'accent',
  ASSIGNED: 'active',
  IN_USE: 'active',
  UNAVAILABLE: 'blocked',

  // Driver
  ELIGIBLE: 'ready',
  CONDITIONAL: 'caution',
  INELIGIBLE: 'blocked',

  // Trips
  PLANNED: 'neutral',
  IN_PROGRESS: 'active',
  ON_HOLD: 'caution',
  COMPLETED: 'ready',
  CANCELLED: 'neutral',

  // Inspections
  PASSED: 'ready',
  PASSED_WITH_DEFECTS: 'caution',
  FAILED: 'blocked',
  DRAFT: 'neutral',
  SUBMITTED: 'active',
  ACCEPTED: 'ready',
  REJECTED: 'blocked',

  // Defects and workflow severity
  ADVISORY: 'neutral',
  MINOR: 'neutral',
  MODERATE: 'caution',
  MAJOR: 'caution',
  CRITICAL: 'blocked',

  // Compliance
  EXPIRING: 'caution',
  EXPIRED: 'blocked',
  SUPERSEDED: 'neutral',
  REVOKED: 'blocked',

  // Workflow status
  OPEN: 'active',
  ESCALATED: 'blocked',
  CLOSED: 'ready',
  REOPENED: 'caution',

  // Workflow priority
  LOW: 'neutral',
  MEDIUM: 'active',
  HIGH: 'caution',
  URGENT: 'blocked',

  // Integration inbox
  PROCESSED: 'ready',
  DEAD_LETTER: 'blocked',

  // Blocker severity
  WARNING: 'caution',
  BLOCKING: 'blocked',

  // Fuel transaction status and lifecycle (S168). RECONCILED is the settled end state; EXCEPTION is
  // the one that puts a case on somebody's queue, so it takes the same tone as a failed inspection.
  RECEIVED: 'neutral',
  VALIDATING: 'active',
  MATCHED: 'active',
  RECONCILED: 'ready',
  EXCEPTION: 'blocked',
  VOIDED: 'neutral',

  // Driver logbook. RETURNED is amber because the record is back with the driver to correct, not
  // because anything failed; RESUBMITTED reads the same as SUBMITTED, which is what it is.
  UNDER_REVIEW: 'active',
  RETURNED: 'caution',
  RESUBMITTED: 'active',
  APPROVED: 'ready',

  // Fuel anomaly case.
  DETECTED: 'caution',
  AWAITING_EXPLANATION: 'caution',
  EXPLANATION_RECEIVED: 'active',
  HELD: 'caution',
};

export const toneFor = (value: string | null | undefined): Tone =>
  value ? (statusTones[value] ?? 'neutral') : 'neutral';

interface StatusChipProps {
  value: string | null | undefined;
  /** Overrides the tone lookup when context changes the meaning of a value. */
  tone?: Tone;
  label?: string;
  size?: 'sm' | 'md';
  className?: string;
}

const StatusChip = ({ value, tone, label, size = 'sm', className }: StatusChipProps) => (
  <span
    className={cn(
      'inline-flex max-w-full items-center rounded-full font-medium',
      size === 'sm' ? 'px-2.5 py-0.5 text-theme-xs' : 'px-3 py-1 text-theme-sm',
      toneStyles[tone ?? toneFor(value)],
      className,
    )}
  >
    <span className="truncate">{label ?? humanise(value)}</span>
  </span>
);

export default StatusChip;
