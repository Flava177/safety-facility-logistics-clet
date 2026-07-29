import type { NotificationChannel } from 'modules/emergency/api/dto';

/**
 * Display helpers particular to S174.
 *
 * The general ones (`formatDateTime`, `formatNumber`) come from `shared/components/format` and the
 * value-object helpers (`siteOf`, `shortId`) from the fuel module unchanged. These exist because
 * this module measures two things nothing else does: elapsed milliseconds against a life-safety
 * target, and reach as a proportion of an audience.
 */

/**
 * Elapsed time from the send command to the last gateway hand-off — "1.2 s", "840 ms".
 *
 * Milliseconds below a second, one decimal above it. The unit changes because the figure is read
 * against a target measured in seconds: "1240 ms" makes an operator do arithmetic to answer a
 * question the screen should have already answered.
 */
export const formatElapsed = (millis: number | null | undefined): string => {
  if (millis === null || millis === undefined) {
    return '—';
  }
  if (millis < 1000) {
    return `${Math.round(millis)} ms`;
  }
  const seconds = millis / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainder = Math.round(seconds % 60);
  return `${minutes} min ${remainder} s`;
};

/** A whole-number percentage, or an em dash when the denominator is zero. */
export const percentOf = (part: number, whole: number): string =>
  whole > 0 ? `${Math.round((100 * part) / whole)}%` : '—';

export const percentValue = (part: number, whole: number): number =>
  whole > 0 ? Math.round((100 * part) / whole) : 0;

export interface ChannelTotals {
  target: number;
  sent: number;
  delivered: number;
  failed: number;
  acknowledged: number;
}

/**
 * The fan-out counters added up across an activation's channels.
 *
 * This is the same arithmetic `ActivationService.deliverySummary` does when it composes the closure
 * summary string, which is why the detail screen's totals and the closure record agree. It is still
 * a derived figure until closure writes it down, and the screen says so.
 */
export const totalsFor = (channels: NotificationChannel[]): ChannelTotals =>
  channels.reduce<ChannelTotals>(
    (running, channel) => ({
      target: running.target + channel.targetCount,
      sent: running.sent + channel.sentCount,
      delivered: running.delivered + channel.deliveredCount,
      failed: running.failed + channel.failedCount,
      acknowledged: running.acknowledged + channel.acknowledgedCount,
    }),
    { target: 0, sent: 0, delivered: 0, failed: 0, acknowledged: 0 },
  );
