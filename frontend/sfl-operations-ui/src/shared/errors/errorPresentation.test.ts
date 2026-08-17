import { describe, expect, it } from 'vitest';
import { FleetApiError, errorDetail, errorLabel } from './FleetApiError';

/**
 * What an operator is shown when something fails.
 *
 * <p>The alert headline used to be the error code with its underscores swapped for spaces, so
 * "FLEET UNEXPECTED STATUS" was the first thing read when anything went wrong - a constant name,
 * shouted, above the sentence that actually explained the problem. This pins the two rules that
 * replaced it: the headline is language, and the code survives as secondary text because support
 * asks for it.
 */
const error = (code: string, message = 'Something happened.', correlationId?: string) =>
  new FleetApiError({ status: 400, code, message, correlationId });

describe('the headline is language, never a code', () => {
  it.each([
    ['FLEET_VALIDATION_FAILED', 'Check what was entered'],
    ['FACILITIES_VALIDATION_FAILED', 'Check what was entered'],
    ['VALIDATION_FAILED', 'Check what was entered'],
    ['UNAUTHORIZED_SCOPE', 'You do not have access to this'],
    ['FLEET_RECORD_NOT_FOUND', 'That record no longer exists'],
    ['FLEET_RECORD_VERSION_CONFLICT', 'Somebody else changed this first'],
    ['DUPLICATE_IDENTIFIER', 'That already exists'],
    ['INVALID_STATE_TRANSITION', 'This cannot be done yet'],
    ['READINESS_BLOCKED', 'This cannot be done yet'],
    ['CLOSURE_EVIDENCE_MISSING', 'This cannot be done yet'],
    ['FLEET_TRANSPORT_FAILURE', 'Could not reach the service'],
    ['FLEET_UNEXPECTED_STATUS', 'Something went wrong'],
    ['FLEET_SERVICE_FAILURE', 'Something went wrong'],
  ])('%s reads as "%s"', (code, expected) => {
    expect(errorLabel(error(code))).toBe(expected);
  });

  it('falls back to a neutral sentence rather than to the code', () => {
    expect(errorLabel(error('SOMETHING_NOBODY_MAPPED'))).toBe('Something went wrong');
    expect(errorLabel(error(''))).toBe('Something went wrong');
  });

  it('never shouts an identifier at the reader', () => {
    const shouted = /^[A-Z][A-Z_ ]+$/;
    for (const code of ['FLEET_UNEXPECTED_STATUS', 'UNAUTHORIZED_SCOPE', 'NOPE']) {
      expect(errorLabel(error(code))).not.toMatch(shouted);
    }
  });
});

describe('the technical half survives, as a footnote', () => {
  it('carries the code and the correlation id together, which is what support asks for', () => {
    expect(errorDetail(error('FLEET_UNEXPECTED_STATUS', 'x', 'abc-123'))).toBe(
      'FLEET_UNEXPECTED_STATUS · correlation abc-123',
    );
  });

  it('carries the code alone when there is no correlation id', () => {
    expect(errorDetail(error('VALIDATION_FAILED'))).toBe('VALIDATION_FAILED');
  });
});

describe('an unmapped service failure does not describe itself in URLs', () => {
  const unmapped = (status: number) =>
    FleetApiError.fromUnmappedFailure(
      status,
      { error: 'Bad Request', path: '/api/v1/facilities/booking-availability/spaces' },
      'abc-123',
    );

  it('names neither the endpoint nor a log the reader cannot reach', () => {
    const message = unmapped(400).message;
    expect(message).not.toContain('/api/');
    expect(message).not.toMatch(/service log/i);
  });

  it('tells the reader what to do instead', () => {
    expect(unmapped(400).message).toMatch(/check the details you entered/i);
    expect(unmapped(400).message).toMatch(/correlation id/i);
  });

  it('separates "your request was refused" from "the service broke"', () => {
    expect(unmapped(400).code).toBe('FLEET_UNEXPECTED_STATUS');
    expect(unmapped(500).code).toBe('FLEET_SERVICE_FAILURE');
    expect(unmapped(500).message).not.toMatch(/check the details/i);
  });
});
