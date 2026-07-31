import type { Tone } from 'shared/components/StatusChip';
import type {
  AssetOperationalStatus,
  BlockerSeverity,
  LocationReadinessStatus,
} from '../api/enums';

/**
 * How S152 values are shown.
 *
 * The tones matter more than they look. `StatusChip`'s shared lookup already maps some of these
 * words — `DEGRADED` is a fleet activation mode, `CRITICAL` is not in it at all — so readiness
 * values are given their tone explicitly here rather than left to a lookup that was written for a
 * different vocabulary. A blocked examination hall rendered in a neutral grey would be a genuinely
 * dangerous piece of styling.
 */

/** Readiness status → chip tone. */
export const readinessTone = (status: LocationReadinessStatus): Tone => {
  switch (status) {
    case 'READY':
      return 'ready';
    case 'DEGRADED':
      return 'caution';
    case 'BLOCKED':
      return 'blocked';
    default:
      // UNKNOWN is neutral, not good. An unassessed hall is not a passed one.
      return 'neutral';
  }
};

/** Blocker severity → chip tone. Advisory is neutral: it is noted, it changes nothing. */
export const severityTone = (severity: BlockerSeverity): Tone => {
  switch (severity) {
    case 'CRITICAL':
      return 'blocked';
    case 'MAJOR':
      return 'caution';
    case 'MINOR':
      return 'caution';
    default:
      return 'neutral';
  }
};

/** Asset operational status → chip tone. */
export const assetStatusTone = (status: AssetOperationalStatus): Tone => {
  switch (status) {
    case 'OPERATIONAL':
      return 'ready';
    case 'DEGRADED':
    case 'UNDER_MAINTENANCE':
      return 'caution';
    case 'OUT_OF_SERVICE':
      return 'blocked';
    default:
      return 'neutral';
  }
};

/** A readiness score's tone, on the same thresholds the dashboard uses for its site score. */
export const scoreTone = (score: number): Tone => {
  if (score >= 90) return 'ready';
  if (score >= 60) return 'caution';
  return 'blocked';
};

/** `EXAMINATION_HALL` → `Examination hall`. */
export const humaniseCode = (value: string | null | undefined): string => {
  if (!value) return '—';
  const words = value.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
};

/**
 * How long ago something happened, in the coarse terms an operator reads.
 *
 * Deliberately imprecise: "3 days ago" is what matters about a stale assessment, not the minute.
 */
export const relativeTime = (iso: string | null | undefined, now = new Date()): string => {
  if (!iso) return 'never';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '—';

  const seconds = Math.round((now.getTime() - then) / 1000);
  if (seconds < 60) return 'just now';

  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;

  const days = Math.round(hours / 24);
  if (days < 31) return `${days} day${days === 1 ? '' : 's'} ago`;

  const months = Math.round(days / 30);
  return `${months} month${months === 1 ? '' : 's'} ago`;
};

/** An ISO date or timestamp as a readable date. */
export const formatDate = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
};

/** An ISO timestamp as a readable date and time. */
export const formatDateTime = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
};

/** `null` and `undefined` render as an em dash rather than as an empty cell. */
export const orDash = (value: string | number | null | undefined): string =>
  value === null || value === undefined || value === '' ? '—' : String(value);
