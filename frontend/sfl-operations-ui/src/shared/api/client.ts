import { FleetApiError } from 'shared/errors/FleetApiError';
import { ApiResponseEnvelope, QueryParams } from './types';
import { fleetApiBaseUrl, sflActor } from './config';

/**
 * The single HTTP entry point for every SFL service call.
 *
 * Header policy, correlation, idempotency and envelope parsing live here so no feature module ever
 * hand-rolls a `fetch`. Every request carries the actor headers the services expect in local
 * development plus an `X-Correlation-ID`; state-creating POSTs carry an `Idempotency-Key`, which
 * the fleet service requires for replay-safe creates.
 */

export const HEADER_USER = 'X-SFL-User';
export const HEADER_DISPLAY_NAME = 'X-SFL-Display-Name';
export const HEADER_ROLES = 'X-SFL-Roles';
export const HEADER_SITES = 'X-SFL-Sites';
export const HEADER_CORRELATION_ID = 'X-Correlation-ID';
export const HEADER_SOURCE_CHANNEL = 'X-SFL-Source-Channel';
export const HEADER_IDEMPOTENCY_KEY = 'Idempotency-Key';

/** RFC 4122 v4 identifier, using the platform generator where available. */
export const newCorrelationId = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = (Math.random() * 16) | 0;
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
};

export const buildQueryString = (params?: QueryParams): string => {
  if (!params) {
    return '';
  }
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    search.append(key, String(value));
  });
  const serialised = search.toString();
  return serialised ? `?${serialised}` : '';
};

export interface RequestOptions {
  query?: QueryParams;
  body?: unknown;
  /** Sends an `Idempotency-Key`; required by the service for state-creating POSTs. */
  idempotent?: boolean;
  signal?: AbortSignal;
}

const buildHeaders = (options: RequestOptions, hasBody: boolean): Headers => {
  const headers = new Headers();
  headers.set('Accept', 'application/json');
  if (hasBody) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set(HEADER_USER, sflActor.user);
  headers.set(HEADER_DISPLAY_NAME, sflActor.displayName);
  headers.set(HEADER_ROLES, sflActor.roles);
  headers.set(HEADER_SITES, sflActor.sites);
  headers.set(HEADER_SOURCE_CHANNEL, sflActor.sourceChannel);
  headers.set(HEADER_CORRELATION_ID, newCorrelationId());
  if (options.idempotent) {
    headers.set(HEADER_IDEMPOTENCY_KEY, newCorrelationId());
  }
  return headers;
};

const parseEnvelope = async <T>(response: Response): Promise<ApiResponseEnvelope<T>> => {
  if (response.status === 204) {
    return { data: null, error: null };
  }
  const text = await response.text();
  if (!text) {
    return { data: null, error: null };
  }
  try {
    return JSON.parse(text) as ApiResponseEnvelope<T>;
  } catch {
    throw new FleetApiError({
      status: response.status,
      code: 'FLEET_MALFORMED_RESPONSE',
      message: 'The service returned a response that could not be read.',
      correlationId: response.headers.get(HEADER_CORRELATION_ID),
    });
  }
};

async function request<T>(
  method: 'GET' | 'POST' | 'PATCH' | 'PUT',
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const hasBody = options.body !== undefined;
  const url = `${fleetApiBaseUrl}${path}${buildQueryString(options.query)}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: buildHeaders(options, hasBody),
      body: hasBody ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw FleetApiError.transport(
      `Could not reach the Fleet service at ${fleetApiBaseUrl}. Check that it is running on port 8093.`,
    );
  }

  const envelope = await parseEnvelope<T>(response);

  if (envelope.error) {
    throw FleetApiError.fromEnvelope(response.status, envelope.error, envelope.data);
  }

  if (!response.ok) {
    throw new FleetApiError({
      status: response.status,
      code: 'FLEET_UNEXPECTED_STATUS',
      message: `The service responded with HTTP ${response.status}.`,
      correlationId: response.headers.get(HEADER_CORRELATION_ID),
    });
  }

  return envelope.data as T;
}

export const apiClient = {
  get: <T>(path: string, query?: QueryParams, signal?: AbortSignal) =>
    request<T>('GET', path, { query, signal }),

  /** POSTs that create state — carries an `Idempotency-Key` by default. */
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('POST', path, { ...options, body, idempotent: options?.idempotent ?? true }),

  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('PATCH', path, { ...options, body }),
};
