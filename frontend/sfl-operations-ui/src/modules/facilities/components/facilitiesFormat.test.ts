import { describe, expect, it } from 'vitest';
import {
  assetStatusTone,
  floorLabel,
  humaniseCode,
  orDash,
  readinessTone,
  relativeTime,
  scoreTone,
  severityTone,
} from './facilitiesFormat';

/**
 * How S152 values are shown.
 *
 * Tone is not decoration here. A blocked examination hall rendered in neutral grey, or an unassessed
 * one rendered green, is a screen actively misleading somebody about whether a room can be used — so
 * the mapping is asserted rather than left to a shared lookup written for a different vocabulary.
 */
describe('readiness rendering', () => {
  it('renders each readiness status in its own tone', () => {
    expect(readinessTone('READY')).toBe('ready');
    expect(readinessTone('DEGRADED')).toBe('caution');
    expect(readinessTone('BLOCKED')).toBe('blocked');
  });

  it('renders UNKNOWN as neutral, never as good', () => {
    // An unassessed hall is not a passed one, and the colour must not suggest otherwise.
    expect(readinessTone('UNKNOWN')).toBe('neutral');
    expect(readinessTone('UNKNOWN')).not.toBe('ready');
  });

  it('renders a critical blocker as blocking and an advisory as neutral', () => {
    expect(severityTone('CRITICAL')).toBe('blocked');
    expect(severityTone('MAJOR')).toBe('caution');
    expect(severityTone('MINOR')).toBe('caution');
    expect(severityTone('ADVISORY')).toBe('neutral');
  });

  it('renders an out-of-service asset as blocking', () => {
    expect(assetStatusTone('OPERATIONAL')).toBe('ready');
    expect(assetStatusTone('DEGRADED')).toBe('caution');
    expect(assetStatusTone('UNDER_MAINTENANCE')).toBe('caution');
    expect(assetStatusTone('OUT_OF_SERVICE')).toBe('blocked');
    // Decommissioned is retired, not broken — it raises no blocker, so it is not alarming.
    expect(assetStatusTone('DECOMMISSIONED')).toBe('neutral');
  });

  it('grades a readiness score', () => {
    expect(scoreTone(100)).toBe('ready');
    expect(scoreTone(90)).toBe('ready');
    expect(scoreTone(89)).toBe('caution');
    expect(scoreTone(60)).toBe('caution');
    expect(scoreTone(59)).toBe('blocked');
    expect(scoreTone(0)).toBe('blocked');
  });
});

describe('value formatting', () => {
  it('humanises a code', () => {
    expect(humaniseCode('EXAMINATION_HALL')).toBe('Examination hall');
    expect(humaniseCode('MOOT_COURTROOM')).toBe('Moot courtroom');
    expect(humaniseCode(null)).toBe('—');
  });

  it('renders an absent value as an em dash rather than an empty cell', () => {
    expect(orDash(null)).toBe('—');
    expect(orDash(undefined)).toBe('—');
    expect(orDash('')).toBe('—');
    // Zero is a value, not an absence — a space with capacity 0 must not read as unrecorded.
    expect(orDash(0)).toBe('0');
  });

  it('describes how long ago something happened', () => {
    const now = new Date('2026-07-30T12:00:00Z');
    const ago = (iso: string) => relativeTime(iso, now);

    expect(ago('2026-07-30T11:59:30Z')).toBe('just now');
    expect(ago('2026-07-30T11:30:00Z')).toBe('30 minutes ago');
    expect(ago('2026-07-30T09:00:00Z')).toBe('3 hours ago');
    expect(ago('2026-07-27T12:00:00Z')).toBe('3 days ago');
    expect(ago('2026-05-30T12:00:00Z')).toBe('2 months ago');
  });

  it('says "never" for a space that has not been assessed', () => {
    // The word matters: "—" would read as missing data rather than as a fact about the space.
    expect(relativeTime(null)).toBe('never');
  });
});

describe('floorLabel', () => {
  it('names a basement as a basement rather than as level minus one', () => {
    // The column is signed because basements are below ground, and "level -1" is not how anybody
    // asks for one.
    expect(floorLabel(-1, 'B1')).toBe('B1 · basement 1');
    expect(floorLabel(-2, 'B2')).toBe('B2 · basement 2');
  });

  it('calls zero the ground floor', () => {
    expect(floorLabel(0, 'GF')).toBe('GF · ground');
  });

  it('says a mezzanine has no level rather than leaving it blank', () => {
    /*
      The column is nullable precisely because a mezzanine sits between two floors and has no honest
      number. A blank cell reads as missing data instead of as the answer — and a client that showed
      null as 0 would file every mezzanine at ground level.
    */
    expect(floorLabel(null, 'MEZZ')).toBe('MEZZ · no level');
  });

  it('leads with the code, which is what is on the signage', () => {
    expect(floorLabel(2, 'L2')).toBe('L2 · level 2');
  });
});
