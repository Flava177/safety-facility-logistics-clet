import { describe, expect, it } from 'vitest';
import { CONFIGURATION_CATALOGUE, describeKey, valueTypeHint } from './configurationCatalogue';

/**
 * The configuration screen's human layer.
 *
 * <p>The screen listed `facilities.readiness.staleness-threshold` and nothing else, which is a
 * setting's name rather than a description of one. What is pinned here is that the label and the
 * effect exist, that a key nobody has described still renders rather than disappearing, and that a
 * duration says what a duration looks like - `PT4H` is not guessable, and an operator typing `4h`
 * and being refused learns only that they were wrong.
 */
describe('every catalogued key says what it does', () => {
  it.each(Object.entries(CONFIGURATION_CATALOGUE))('%s carries a label and an effect', (_key, entry) => {
    expect(entry.label.trim().length).toBeGreaterThan(0);
    expect(entry.effect.trim().length).toBeGreaterThan(0);
    // A label is not the key with the dots taken out - it has to read as English.
    expect(entry.label).not.toMatch(/[._]/);
  });

  it('covers the thresholds the operations guide and the CMMS design document', () => {
    // These are the ones an operator actually reaches for; a regression that dropped them would
    // leave the screen quietly falling back to the raw key.
    expect(CONFIGURATION_CATALOGUE['facilities.readiness.staleness-threshold']).toBeDefined();
    expect(CONFIGURATION_CATALOGUE['maintenance.sla.resolution.critical']).toBeDefined();
    expect(CONFIGURATION_CATALOGUE['maintenance.readiness.blocker-threshold']).toBeDefined();
    expect(CONFIGURATION_CATALOGUE['booking.no-show.grace']).toBeDefined();
  });
});

describe('a key nobody has described still renders', () => {
  it('falls back to the key and whatever the service said about it', () => {
    const entry = describeKey('facilities.something.new-threshold', 'What the service says it does.');
    expect(entry.label).toBe('Something new threshold');
    expect(entry.effect).toBe('What the service says it does.');
  });

  it('says so plainly when the service described nothing either', () => {
    expect(describeKey('facilities.mystery.key', null).effect).toContain('No description');
  });

  it('prefers the catalogue over the service description where both exist', () => {
    const entry = describeKey('facilities.readiness.staleness-threshold', 'Terse service wording.');
    expect(entry.effect).not.toBe('Terse service wording.');
    expect(entry.label).toBe('Readiness goes stale after');
  });
});

describe('the value format is stated rather than discovered', () => {
  it('gives ISO-8601 examples for a duration', () => {
    expect(valueTypeHint('DURATION')).toContain('P7D');
    expect(valueTypeHint('DURATION')).toContain('PT4H');
  });

  it('has nothing to add for a plain string', () => {
    expect(valueTypeHint('STRING')).toBeUndefined();
    expect(valueTypeHint(null)).toBeUndefined();
  });
});
