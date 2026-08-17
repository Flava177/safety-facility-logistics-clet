/**
 * What each configuration key is, in the words of somebody who has to decide what to set it to.
 *
 * <h2>Why a catalogue rather than the raw key</h2>
 *
 * <p>The screen listed `facilities.readiness.staleness-threshold` in a monospace column and nothing
 * else. That is the name of a setting, not a description of one, and an operator deciding whether
 * seven days is right for their centre cannot get there from the string. The label and the effect
 * below are transcribed from `S152_Operations_And_Verification_Guide.md` §6 and
 * `S153_CMMS_Design.md` §3 - the documents that already state what each key does - so this file is a
 * transcription rather than an invention, and where the two disagree the document is corrected.
 *
 * <h2>The raw key stays visible</h2>
 *
 * <p>As secondary text, not as the primary column. Support reads a log line naming the key and has
 * to find the row; an operator reads the label. Both are served by showing both, and only one of
 * them is served by showing the key alone.
 *
 * <h2>What happens to a key that is not here</h2>
 *
 * <p>It renders with its key humanised as the label and the service's own `description` as the
 * effect - which every seeded value carries. So a key added to the service later appears correctly
 * without this file being touched, and is merely less well described until somebody writes an entry.
 * Nothing is hidden for being unknown.
 */

export interface ConfigurationEntry {
  label: string;
  /** One line: what changes when this changes. */
  effect: string;
}

export const CONFIGURATION_CATALOGUE: Record<string, ConfigurationEntry> = {
  // ---- S152, from the operations guide §6 ------------------------------------------------------
  'facilities.readiness.staleness-threshold': {
    label: 'Readiness goes stale after',
    effect:
      'How long a space readiness assessment stands before the dashboard reports it as stale and asks for a fresh one.',
  },
  'facilities.readiness.examination-staleness-threshold': {
    label: 'Readiness goes stale after, in examination mode',
    effect:
      'The same window while the centre is running examinations, where it is deliberately much tighter.',
  },
  'facilities.dashboard.freshness-threshold': {
    label: 'Dashboard warns its figures are old after',
    effect:
      'How old the dashboard snapshot may be before the screen carries a stale-data warning rather than presenting the numbers plainly.',
  },
  'facilities.dashboard.default-page-size': {
    label: 'Rows per dashboard drilldown',
    effect: 'How many records a drilldown returns when the caller does not ask for a page size.',
  },
  'facilities.blocker.critical-escalation-window': {
    label: 'Critical blocker escalates after',
    effect:
      'How long an open critical readiness blocker may age before it is reported as escalated. This does not resolve anything - it decides when the estate stops treating it as routine.',
  },
  'facilities.asset.service-due-warning-window': {
    label: 'Warn a service is due',
    effect: 'How far ahead of its due date an asset service is reported as coming up.',
  },
  'facilities.asset.warranty-warning-window': {
    label: 'Warn a warranty is expiring',
    effect: 'How far ahead of expiry an asset warranty is reported.',
  },
  'facilities.device.staleness-threshold': {
    label: 'Device status goes stale after',
    effect:
      'How old a vendor-reported device status may be before it is treated as stale. Measured from the vendor’s own observation time, not from when it reached us.',
  },
  'facilities.outbound.max-attempts': {
    label: 'Outbound delivery attempts',
    effect:
      'How many times an outbound message is retried before it is dead-lettered. Reserved for the outbox drainer.',
  },
  'facilities.outbound.retry-base-seconds': {
    label: 'Outbound retry, first wait',
    effect: 'The base of the exponential backoff between outbound retries.',
  },
  'facilities.outbound.retry-max-seconds': {
    label: 'Outbound retry, longest wait',
    effect: 'The ceiling on that backoff, however many attempts have been made.',
  },

  // ---- S153, from the CMMS design §3 -----------------------------------------------------------
  'maintenance.sla.resolution.critical': {
    label: 'Time to resolve a critical fault',
    effect: 'The work order deadline for a critical fault. Overrunning it is what starts escalation.',
  },
  'maintenance.sla.resolution.high': {
    label: 'Time to resolve a high-priority fault',
    effect: 'The work order deadline for a high-priority fault.',
  },
  'maintenance.sla.resolution.medium': {
    label: 'Time to resolve a medium-priority fault',
    effect: 'The work order deadline for a medium-priority fault.',
  },
  'maintenance.sla.resolution.low': {
    label: 'Time to resolve a low-priority fault',
    effect: 'The work order deadline for a low-priority fault.',
  },
  'maintenance.sla.response.critical': {
    label: 'Time to acknowledge a critical fault',
    effect: 'How long before a critical fault must be acknowledged. Carried for a later round.',
  },
  'maintenance.sla.response.high': {
    label: 'Time to acknowledge a high-priority fault',
    effect: 'How long before a high-priority fault must be acknowledged.',
  },
  'maintenance.sla.response.medium': {
    label: 'Time to acknowledge a medium-priority fault',
    effect: 'How long before a medium-priority fault must be acknowledged.',
  },
  'maintenance.sla.response.low': {
    label: 'Time to acknowledge a low-priority fault',
    effect: 'How long before a low-priority fault must be acknowledged.',
  },
  'maintenance.sla.examination-factor': {
    label: 'Deadlines tighten in examination mode by',
    effect:
      'Every maintenance deadline is multiplied by this while the centre is in examination mode. A value of 0.5 halves them.',
  },
  'maintenance.escalation.interval': {
    label: 'Escalate again after',
    effect:
      'How long between successive escalation levels once a work order is overdue. Each level notifies further up.',
  },
  'maintenance.escalation.max-level': {
    label: 'Highest escalation level',
    effect: 'The top of the escalation ladder. Nothing escalates beyond it, however long it runs.',
  },
  'maintenance.readiness.blocker-threshold': {
    label: 'A fault blocks its space from priority',
    effect:
      'The fault priority at which reporting a fault takes the space out of use. Lowering it makes the estate more cautious and closes more rooms.',
  },
  'maintenance.closure.evidence-threshold': {
    label: 'Closure evidence required from priority',
    effect:
      'The fault priority at or above which a work order cannot be closed without evidence attached.',
  },
  'maintenance.closure.evidence-count': {
    label: 'Evidence items required to close',
    effect: 'How many items must be attached, for work orders above the threshold.',
  },
  'maintenance.preventive.generation-batch': {
    label: 'Schedules per generation run',
    effect: 'How many preventive schedules are processed in one generation run.',
  },

  // ---- S159 ------------------------------------------------------------------------------------
  'booking.approval.all-in-examination-mode': {
    label: 'Every booking needs approval in examination mode',
    effect:
      'While the centre is in examination mode, every booking requires an approver whatever its purpose.',
  },
  'booking.approval.duration-threshold': {
    label: 'Bookings longer than this need approval',
    effect: 'A booking longer than this requires an approver whatever its purpose.',
  },
  'booking.approval.purposes': {
    label: 'Purposes that always need approval',
    effect: 'Booking purposes that require an approver regardless of length or mode.',
  },
  'booking.horizon.days': {
    label: 'How far ahead a room may be booked',
    effect: 'The furthest into the future a booking may be made.',
  },
  'booking.no-show.grace': {
    label: 'No-show grace period',
    effect:
      'How long after its start a confirmed booking may go unused before the sweep releases the room as a no-show.',
  },
  'booking.setup.default-minutes': {
    label: 'Setup buffer before a booking',
    effect: 'Time reserved before a booking for setting the room up.',
  },
  'booking.setup.examination-minutes': {
    label: 'Setup buffer before an examination',
    effect: 'The same, for an examination, which needs the layout changing.',
  },
  'booking.teardown.default-minutes': {
    label: 'Teardown buffer after a booking',
    effect: 'Time reserved after a booking for clearing the room.',
  },
  'booking.teardown.examination-minutes': {
    label: 'Teardown buffer after an examination',
    effect: 'The same, for an examination, which needs the layout changing back.',
  },
  'booking.sweep.batch': {
    label: 'Rows per booking sweep',
    effect: 'How many rows a reconciliation or no-show sweep processes at a time.',
  },
};

/**
 * How a value of this type is written, and what a legal one looks like.
 *
 * `PT4H` is not guessable, and an operator typing `4h` and being refused by the service learns only
 * that they were wrong. ISO-8601 durations are the format the service parses, so the format is
 * stated on the field rather than discovered.
 */
export const valueTypeHint = (valueType: string | null): string | undefined => {
  switch (valueType) {
    case 'DURATION':
      return 'ISO-8601 duration: P7D is seven days, PT4H is four hours, PT30M is thirty minutes.';
    case 'INTEGER':
      return 'A whole number.';
    case 'DECIMAL':
      return 'A number, decimals allowed - 0.5 halves whatever it multiplies.';
    case 'BOOLEAN':
      return 'true or false.';
    default:
      return undefined;
  }
};

/** A key's catalogue entry, or one derived from the key and whatever the service said about it. */
export const describeKey = (key: string, serviceDescription: string | null): ConfigurationEntry => {
  const known = CONFIGURATION_CATALOGUE[key];
  if (known) {
    return known;
  }
  const words = key.split('.').slice(1).join(' ').replace(/-/g, ' ');
  return {
    label: words.charAt(0).toUpperCase() + words.slice(1),
    effect: serviceDescription ?? 'No description is recorded for this key.',
  };
};
