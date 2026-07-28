import { Chip, ChipProps } from '@mui/material';
import { humanise } from 'modules/fleet/api/enums';

type Tone = 'ready' | 'caution' | 'blocked' | 'neutral' | 'active' | 'accent';

const toneToColor: Record<Tone, ChipProps['color']> = {
  ready: 'success',
  caution: 'warning',
  blocked: 'error',
  neutral: 'neutral',
  active: 'info',
  accent: 'secondary',
};

/**
 * Status vocabulary for the whole console.
 *
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
};

interface StatusChipProps extends Omit<ChipProps, 'color' | 'label'> {
  value: string | null | undefined;
  /** Overrides the tone lookup when context changes the meaning of a value. */
  tone?: Tone;
  label?: string;
}

const StatusChip = ({ value, tone, label, size = 'small', ...rest }: StatusChipProps) => {
  const resolvedTone: Tone = tone ?? (value ? (statusTones[value] ?? 'neutral') : 'neutral');

  return (
    <Chip
      {...rest}
      size={size}
      variant="soft"
      color={toneToColor[resolvedTone]}
      label={label ?? humanise(value)}
    />
  );
};

export default StatusChip;
