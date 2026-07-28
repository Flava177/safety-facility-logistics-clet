import { ApiErrorEnvelope, FieldErrorPayload } from 'shared/api/types';

/**
 * A failed SFL API call, normalised.
 *
 * The service returns the SRS *Error States* wording verbatim for SRS-defined codes, so `message`
 * is safe to show to an operator as-is. `fieldErrors` is populated for `FLEET_VALIDATION_FAILED`
 * and drives inline field errors; `details` carries the domain exception's context map (for example
 * the readiness blockers behind `FLEET_READINESS_BLOCKED`).
 */
export class FleetApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId: string | null;
  readonly fieldErrors: FieldErrorPayload[];
  readonly details: Record<string, unknown> | null;

  constructor(init: {
    status: number;
    code: string;
    message: string;
    correlationId?: string | null;
    fieldErrors?: FieldErrorPayload[];
    details?: Record<string, unknown> | null;
  }) {
    super(init.message);
    this.name = 'FleetApiError';
    this.status = init.status;
    this.code = init.code;
    this.correlationId = init.correlationId ?? null;
    this.fieldErrors = init.fieldErrors ?? [];
    this.details = init.details ?? null;
  }

  /** `true` when the caller can fix the request by editing form fields. */
  get isFieldValidation(): boolean {
    return this.fieldErrors.length > 0;
  }

  /** `true` when the record moved under the caller and the screen should reload before retrying. */
  get isVersionConflict(): boolean {
    return this.code === 'FLEET_RECORD_VERSION_CONFLICT' || this.status === 409;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  /** Maps `fieldErrors` into the `{ field: message }` shape the form layer consumes. */
  toFieldErrorMap(): Record<string, string> {
    return this.fieldErrors.reduce<Record<string, string>>((accumulator, fieldError) => {
      const key = fieldError.field.includes('.')
        ? fieldError.field.slice(fieldError.field.lastIndexOf('.') + 1)
        : fieldError.field;
      if (!accumulator[key]) {
        accumulator[key] = fieldError.message;
      }
      return accumulator;
    }, {});
  }

  static fromEnvelope(status: number, error: ApiErrorEnvelope, data: unknown): FleetApiError {
    const fieldErrors = Array.isArray(data) ? (data as FieldErrorPayload[]) : [];
    const details =
      !Array.isArray(data) && data !== null && typeof data === 'object'
        ? (data as Record<string, unknown>)
        : null;

    return new FleetApiError({
      status,
      code: error.code,
      message: error.message,
      correlationId: error.correlationId,
      fieldErrors,
      details,
    });
  }

  static transport(message: string): FleetApiError {
    return new FleetApiError({ status: 0, code: 'FLEET_TRANSPORT_FAILURE', message });
  }

  /**
   * A failure that did not arrive in the SFL envelope.
   *
   * <p>Spring's default error body (`{ timestamp, status, error, message, path }`) is the common
   * case — an exception the service does not map, so its handlers never ran. Anything readable in
   * that body is used, because "Internal Server Error at /api/v1/fleet/trips" tells an operator far
   * more than a bare status code, and the server log will have the stack trace.
   */
  static fromUnmappedFailure(
    status: number,
    body: unknown,
    correlationId?: string | null,
  ): FleetApiError {
    const shape = (body ?? {}) as Record<string, unknown>;
    const reason = typeof shape.error === 'string' ? shape.error : undefined;
    const detail = typeof shape.message === 'string' ? shape.message : undefined;
    const path = typeof shape.path === 'string' ? shape.path : undefined;

    const summary = [reason, detail].filter(Boolean).join(' — ') || `HTTP ${status}`;

    return new FleetApiError({
      status,
      code: status >= 500 ? 'FLEET_SERVICE_FAILURE' : 'FLEET_UNEXPECTED_STATUS',
      message: path
        ? `The service could not complete ${path}: ${summary}. Check the service log for the cause.`
        : `The service responded with ${summary}.`,
      correlationId,
    });
  }
}

/** `true` only for a real `ApiError` — a `code` and a `message`, both strings. */
export const isApiErrorEnvelope = (value: unknown): value is ApiErrorEnvelope => {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Partial<ApiErrorEnvelope>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string';
};

/** The error code as a display label, safe for any shape that reached the UI. */
export const errorLabel = (error: FleetApiError): string =>
  (error.code || 'ERROR').replace(/_/g, ' ');

/** `true` for anything thrown by the API client. */
export const isFleetApiError = (value: unknown): value is FleetApiError =>
  value instanceof FleetApiError;

/** The operator-facing headline for an unknown failure. */
export const describeError = (error: unknown): string => {
  if (isFleetApiError(error)) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'An unexpected error occurred.';
};
