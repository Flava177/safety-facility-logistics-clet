import { describe, expect, it } from 'vitest';
import type { VisitorVisit } from './dto';
import { visitorWorkflow } from './workflow';

const visit = (status: VisitorVisit['status'], badgeNumber: string | null = null) => ({ status, badgeNumber }) as VisitorVisit;

describe('visitor workflow controls', () => {
  it('offers a host decision only while pre-registered', () => {
    expect(visitorWorkflow.canDecide(visit('PRE_REGISTERED'))).toBe(true);
    expect(visitorWorkflow.canDecide(visit('CONFIRMED'))).toBe(false);
  });
  it('requires a confirmed visit and a badge before check-in', () => {
    expect(visitorWorkflow.canCheckIn(visit('CONFIRMED'))).toBe(false);
    expect(visitorWorkflow.canCheckIn(visit('CONFIRMED', 'B-12'))).toBe(true);
  });
  it('offers check-out only to somebody currently on site', () => {
    expect(visitorWorkflow.canCheckOut(visit('CHECKED_IN', 'B-12'))).toBe(true);
    expect(visitorWorkflow.canCheckOut(visit('CHECKED_OUT', 'B-12'))).toBe(false);
  });
  it('does not offer cancellation after arrival or a terminal outcome', () => {
    expect(visitorWorkflow.canCancel(visit('PRE_REGISTERED'))).toBe(true);
    expect(visitorWorkflow.canCancel(visit('CONFIRMED'))).toBe(true);
    expect(visitorWorkflow.canCancel(visit('CHECKED_IN'))).toBe(false);
    expect(visitorWorkflow.canCancel(visit('REJECTED'))).toBe(false);
  });
});
