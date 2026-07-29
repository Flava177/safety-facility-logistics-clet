/**
 * Client-side mirrors of the fleet service's Bean Validation rules.
 *
 * These exist to give an operator an answer before a round trip — never to replace the server
 * check. Every mutation still submits and still maps the service's `fieldErrors` back onto the
 * form, so a rule that drifts here cannot let bad data through.
 */

export type FieldValidator = (value: unknown) => string | undefined;

export const required =
  (label: string): FieldValidator =>
  (value) => {
    if (value === undefined || value === null) {
      return `${label} is required.`;
    }
    if (typeof value === 'string' && value.trim() === '') {
      return `${label} is required.`;
    }
    if (Array.isArray(value) && value.length === 0) {
      return `${label} is required.`;
    }
    return undefined;
  };

export const maxLength =
  (label: string, limit: number): FieldValidator =>
  (value) =>
    typeof value === 'string' && value.length > limit
      ? `${label} must be ${limit} characters or fewer.`
      : undefined;

/** `@PositiveOrZero Long` on the service side: whole number, not negative. */
export const nonNegativeInteger =
  (label: string): FieldValidator =>
  (value) => {
    if (value === undefined || value === null || value === '') {
      return undefined;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return `${label} must be a number.`;
    }
    if (!Number.isInteger(parsed)) {
      return `${label} must be a whole number.`;
    }
    if (parsed < 0) {
      return `${label} cannot be negative.`;
    }
    return undefined;
  };

/**
 * `@PositiveOrZero BigDecimal` on the service side: a decimal, not negative.
 *
 * Distinct from `nonNegativeInteger` because money and quantities are `BigDecimal` on the fuel
 * aggregates — a litre count of 20.5 and a unit price of 10.4750 are both legal, and rejecting them
 * as "not a whole number" would be the client inventing a rule the service does not have.
 */
export const nonNegativeNumber =
  (label: string): FieldValidator =>
  (value) => {
    if (value === undefined || value === null || value === '') {
      return undefined;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return `${label} must be a number.`;
    }
    return parsed < 0 ? `${label} cannot be negative.` : undefined;
  };

/** `@Positive BigDecimal`: a decimal strictly greater than zero. */
export const positiveNumber =
  (label: string): FieldValidator =>
  (value) => {
    if (value === undefined || value === null || value === '') {
      return undefined;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return `${label} must be a number.`;
    }
    return parsed <= 0 ? `${label} must be greater than zero.` : undefined;
  };

/** `@Positive int`: a whole number of at least `floor` (1 for `@Positive`, 0 for `@PositiveOrZero`). */
export const integerAtLeast =
  (label: string, floor: number): FieldValidator =>
  (value) => {
    if (value === undefined || value === null || value === '') {
      return undefined;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return `${label} must be a number.`;
    }
    if (!Number.isInteger(parsed)) {
      return `${label} must be a whole number.`;
    }
    return parsed < floor ? `${label} must be at least ${floor}.` : undefined;
  };

export const numberBetween =
  (label: string, min: number, max: number): FieldValidator =>
  (value) => {
    if (value === undefined || value === null || value === '') {
      return undefined;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return `${label} must be a number.`;
    }
    if (parsed < min || parsed > max) {
      return `${label} must be between ${min} and ${max}.`;
    }
    return undefined;
  };

export const validDateTime =
  (label: string): FieldValidator =>
  (value) => {
    if (!value) {
      return undefined;
    }
    return Number.isNaN(new Date(String(value)).getTime())
      ? `${label} is not a valid date.`
      : undefined;
  };

/** Composes validators, returning the first failure. */
export const compose =
  (...validators: FieldValidator[]): FieldValidator =>
  (value) => {
    for (const validate of validators) {
      const message = validate(value);
      if (message) {
        return message;
      }
    }
    return undefined;
  };

/**
 * Range check across two fields.
 *
 * Returned against the end field, which is where an operator looks when a range is wrong.
 */
export const dateRangeError = (
  start: string | undefined,
  end: string | undefined,
  startLabel = 'Start',
  endLabel = 'End',
): string | undefined => {
  if (!start || !end) {
    return undefined;
  }
  const startAt = new Date(start).getTime();
  const endAt = new Date(end).getTime();
  if (Number.isNaN(startAt) || Number.isNaN(endAt)) {
    return undefined;
  }
  return endAt <= startAt ? `${endLabel} must be after ${startLabel.toLowerCase()}.` : undefined;
};

/** Odometer readings may not move backwards outside an authorised correction. */
export const odometerNotBelow = (
  reading: string | number | undefined,
  floor: number | undefined,
  floorLabel = 'the last recorded reading',
): string | undefined => {
  if (reading === undefined || reading === '' || floor === undefined) {
    return undefined;
  }
  const parsed = Number(reading);
  if (!Number.isFinite(parsed)) {
    return undefined;
  }
  return parsed < floor
    ? `Reading cannot be lower than ${floorLabel} (${floor.toLocaleString()}).`
    : undefined;
};
