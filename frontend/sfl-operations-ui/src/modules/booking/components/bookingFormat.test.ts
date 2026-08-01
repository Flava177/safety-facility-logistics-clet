import { describe, expect, it } from 'vitest';
import type { Booking } from '../api/dto';
import {
  bufferSummary,
  formatWindow,
  fromLocalInput,
  nextHourLocalInput,
  plusHoursLocalInput,
  toLocalInput,
  windowProblem,
} from './bookingFormat';

/**
 * The formatting decisions worth pinning down.
 *
 * Tones and labels are not tested — they are taste, and a test of taste is a test that fails on every
 * design change. What is tested is the two places where getting it wrong produces a **wrong booking**
 * rather than an ugly one: the local/UTC round trip, and the window validation that decides whether a
 * request is sent at all.
 */

const booking = (setupMinutes: number, teardownMinutes: number): Booking =>
  ({ setupMinutes, teardownMinutes }) as Booking;

describe('the local/UTC round trip', () => {
  /*
    `datetime-local` is local wall-clock time with no zone; the wire is UTC. Getting this backwards
    books a room at the wrong hour, and the operator sees the hour they typed on the way back, so the
    error is invisible until somebody arrives at an empty hall.
  */
  it('survives a round trip through the wire format', () => {
    const iso = '2026-08-04T09:30:00.000Z';
    expect(fromLocalInput(toLocalInput(iso))).toBe(iso);
  });

  it('produces the shape the datetime-local control wants', () => {
    expect(toLocalInput('2026-08-04T09:30:00.000Z')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it('lands the default start on a whole hour in the future', () => {
    const now = new Date('2026-08-04T09:17:00');
    const start = nextHourLocalInput(now);

    expect(start.endsWith(':00')).toBe(true);
    expect(new Date(start).getTime()).toBeGreaterThan(now.getTime());
  });

  it('adds hours in local time, so a default window does not drift across a zone', () => {
    const start = '2026-08-04T09:00';
    expect(plusHoursLocalInput(start, 2)).toBe('2026-08-04T11:00');
  });
});

describe('window validation', () => {
  /*
    Only the two rules that need no context. Whether the window has passed, whether it is inside the
    site's booking horizon and whether the space is free are decisions with context that the service
    owns — guessing at them here would produce a screen refusing bookings the estate would have taken,
    which is the failure nobody reports because it looks like the rules.
  */
  it('refuses an end at or before its start', () => {
    expect(windowProblem('2026-08-04T09:00', '2026-08-04T09:00')).toContain('after the start');
    expect(windowProblem('2026-08-04T11:00', '2026-08-04T09:00')).toContain('after the start');
  });

  it('refuses a half-filled window', () => {
    expect(windowProblem('', '2026-08-04T09:00')).not.toBeNull();
    expect(windowProblem('2026-08-04T09:00', '')).not.toBeNull();
  });

  it('accepts a well-formed window and says nothing about the past', () => {
    expect(windowProblem('2026-08-04T09:00', '2026-08-04T11:00')).toBeNull();
    // Deliberately allowed through: the service decides whether a past window is bookable.
    expect(windowProblem('2020-01-01T09:00', '2020-01-01T11:00')).toBeNull();
  });
});

describe('the buffer summary', () => {
  it('says nothing when a booking has no buffers', () => {
    expect(bufferSummary(booking(0, 0))).toBeNull();
  });

  it('names each buffer that exists', () => {
    expect(bufferSummary(booking(30, 0))).toContain('30 min before');
    expect(bufferSummary(booking(0, 15))).toContain('15 min after');
    expect(bufferSummary(booking(30, 15))).toBe('Holds the space 30 min before and 15 min after');
  });
});

describe('formatWindow', () => {
  it('states the date once for a booking inside one day', () => {
    const formatted = formatWindow('2026-08-04T09:00:00Z', '2026-08-04T11:00:00Z');
    expect(formatted).toContain('–');
    expect(formatted.match(/Aug/g) ?? []).toHaveLength(1);
  });

  it('states both dates when a booking spans days', () => {
    // An overnight setup is real — an examination hall laid out the evening before.
    const formatted = formatWindow('2026-08-04T22:00:00Z', '2026-08-05T06:00:00Z');
    expect(formatted.match(/Aug/g) ?? []).toHaveLength(2);
  });
});
