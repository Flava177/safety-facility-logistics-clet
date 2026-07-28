import dayjs from 'dayjs';

/** Display helpers. Everything renders in the operator's local zone; the wire stays ISO-8601 UTC. */

export const formatDateTime = (value: string | null | undefined): string =>
  value ? dayjs(value).format('DD MMM YYYY HH:mm') : '—';

export const formatDate = (value: string | null | undefined): string =>
  value ? dayjs(value).format('DD MMM YYYY') : '—';

export const formatNumber = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : value.toLocaleString();

export const formatOdometer = (value: number | null | undefined, unit = 'KILOMETRES'): string =>
  value === null || value === undefined
    ? '—'
    : `${value.toLocaleString()} ${unit === 'MILES' ? 'mi' : 'km'}`;

/** "in 12 days" / "9 days ago" — the phrasing compliance and licence expiry screens need. */
export const formatDaysRemaining = (days: number | null | undefined): string => {
  if (days === null || days === undefined) {
    return '—';
  }
  if (days === 0) {
    return 'Expires today';
  }
  return days > 0
    ? `${days} day${days === 1 ? '' : 's'} left`
    : `${Math.abs(days)} day${days === -1 ? '' : 's'} overdue`;
};

/** `datetime-local` input value from an ISO instant, and back. */
export const toLocalInputValue = (value: string | null | undefined): string =>
  value ? dayjs(value).format('YYYY-MM-DDTHH:mm') : '';

export const fromLocalInputValue = (value: string): string =>
  value ? dayjs(value).toISOString() : '';

export const nowLocalInputValue = (offsetHours = 0): string =>
  dayjs().add(offsetHours, 'hour').format('YYYY-MM-DDTHH:mm');

export const todayIsoDate = (): string => dayjs().format('YYYY-MM-DD');
