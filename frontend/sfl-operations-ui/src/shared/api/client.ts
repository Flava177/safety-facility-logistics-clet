import { readSession } from 'shared/auth/session';
import { FleetApiError, isApiErrorEnvelope } from 'shared/errors/FleetApiError';
import { ApiResponseEnvelope, QueryParams } from './types';
import { emergencyApiBaseUrl, facilitiesApiBaseUrl, fleetApiBaseUrl, sflActor } from './config';

/**
 * The single HTTP entry point for every SFL service call.
 *
 * Header policy, correlation, idempotency and envelope parsing live here so no feature module ever
 * hand-rolls a `fetch`. Every request carries the actor headers the services expect in local
 * development plus an `X-Correlation-ID`; state-creating POSTs carry an `Idempotency-Key`, which
 * the services require for replay-safe creates.
 *
 * Calls address one of three services. Fleet, fuel and dispatch are three modules of the one
 * `sfl-fleet-logistics-service`; emergency notification (S174) and facilities (S152, and in time
 * S153 and S159) are services of their own on other ports. The envelope, the actor headers and the
 * error catalogue are identical across all three — only the origin differs — so the target is a
 * per-call option rather than three clients.
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

/**
 * Which SFL service a call is addressed to.
 *
 * `fleet` covers the fleet, fuel and dispatch modules — one service, one origin. `emergency` is
 * the separate S174 notification service. `facilities` is the IFIMP service: S152 today, S153 and
 * S159 behind the same origin later. Named rather than passed as a raw URL so a module cannot
 * quietly point at something that is not an SFL service.
 */
export type SflService = 'fleet' | 'emergency' | 'facilities';

const serviceOrigins: Record<SflService, string> = {
  fleet: fleetApiBaseUrl,
  emergency: emergencyApiBaseUrl,
  facilities: facilitiesApiBaseUrl,
};

const serviceNames: Record<SflService, string> = {
  fleet: 'Fleet & Logistics service',
  emergency: 'Emergency Notification service',
  facilities: 'Facilities service',
};

const servicePorts: Record<SflService, string> = {
  fleet: '8093',
  emergency: '8095',
  facilities: '8091',
};

const unreachable = (service: SflService): FleetApiError =>
  FleetApiError.transport(
    `Could not reach the ${serviceNames[service]} at ${serviceOrigins[service] || 'this origin'}. ` +
      `Check that it is running on port ${servicePorts[service]}.`,
  );

export interface RequestOptions {
  query?: QueryParams;
  body?: unknown;
  /** Sends an `Idempotency-Key`; required by the service for state-creating POSTs. */
  idempotent?: boolean;
  signal?: AbortSignal;
  /** Which service to call. Defaults to fleet, which is where most of the dashboard lives. */
  service?: SflService;
  /**
   * Overrides `Accept` for an endpoint that does not produce JSON.
   *
   * Spring matches the handler's `produces` against `Accept` and answers 406 when they do not
   * intersect — so a `text/csv` report asked for with `Accept: application/json` is refused before
   * it is ever generated.
   */
  accept?: string;
}

const buildHeaders = (options: RequestOptions, hasJsonBody: boolean): Headers => {
  const headers = new Headers();
  headers.set('Accept', options.accept ?? 'application/json');
  if (hasJsonBody) {
    headers.set('Content-Type', 'application/json');
  }
  /*
    The bearer token, when this browser has a session.

    Added with the login page. Before it, this client sent the X-SFL-* headers and no Authorization
    header at all — so A1's resource server, JWT resolvers and imported realm were unreachable from
    the dashboard, and the whole UI only worked against a service running with security switched off.

    Both are sent, and the services prefer the verified principal: with `SFL_SECURITY_ENABLED=false`
    the headers are the only identity there is, and with security on the JWT wins and the headers are
    ignored. There is deliberately no mode in which a header can override a token — that ordering is
    what makes sending both safe rather than merely convenient.
  */
  const session = readSession();
  if (session) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
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
  method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE',
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const hasBody = options.body !== undefined;
  // A `FormData` body is sent as-is: the browser has to write the multipart boundary into the
  // Content-Type itself, so setting that header by hand produces a request the server cannot parse.
  const multipart = hasBody && options.body instanceof FormData;
  const service = options.service ?? 'fleet';
  const url = `${serviceOrigins[service]}${path}${buildQueryString(options.query)}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: buildHeaders(options, hasBody && !multipart),
      body: multipart ? (options.body as FormData) : hasBody ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw unreachable(service);
  }

  const envelope = await parseEnvelope<T>(response);
  const correlationId = response.headers.get(HEADER_CORRELATION_ID);

  if (isApiErrorEnvelope(envelope.error)) {
    throw FleetApiError.fromEnvelope(response.status, envelope.error, envelope.data);
  }

  if (!response.ok) {
    // Not every failure comes back in the SFL envelope. An exception the service does not map —
    // or anything raised before its handlers run — produces Spring's own error body, and a
    // reverse proxy can produce something else entirely. Those must still surface as a readable
    // error rather than crashing the screen that is rendering it.
    throw FleetApiError.fromUnmappedFailure(response.status, envelope, correlationId);
  }

  return envelope.data as T;
}

/**
 * Fetches a file endpoint and hands it to the browser as a download.
 *
 * A report is `text/csv`, not the SFL envelope, so it cannot go through `request`. It also cannot be
 * a plain link or `window.open`: the services authorise from the `X-SFL-*` headers, and a browser
 * navigation carries none of them — the request would arrive as an anonymous actor and be refused.
 * So it is fetched with the same headers as everything else and saved from a blob.
 *
 * A failure still arrives in the envelope (the exception handler runs before the CSV is written), so
 * it is parsed and thrown as a `FleetApiError` exactly like any other call.
 */
export async function downloadFile(
  path: string,
  query?: QueryParams,
  fallbackFileName = 'download',
  /**
   * The media type the endpoint produces, plus JSON for the error path.
   *
   * Both matter. Without the first, Spring answers 406 and the report is never generated; without
   * the second, an authorisation refusal — which comes back as a JSON envelope — would itself be
   * rejected for the wrong content type, and the operator would see a content negotiation failure
   * instead of "you are not authorised".
   */
  accept = 'text/csv, application/json',
  service: SflService = 'fleet',
): Promise<string> {
  const url = `${serviceOrigins[service]}${path}${buildQueryString(query)}`;

  let response: Response;
  try {
    response = await fetch(url, { method: 'GET', headers: buildHeaders({ accept }, false) });
  } catch {
    throw unreachable(service);
  }

  if (!response.ok) {
    const text = await response.text();
    const correlationId = response.headers.get(HEADER_CORRELATION_ID);
    try {
      const envelope = JSON.parse(text) as ApiResponseEnvelope<unknown>;
      if (isApiErrorEnvelope(envelope.error)) {
        throw FleetApiError.fromEnvelope(response.status, envelope.error, envelope.data);
      }
      throw FleetApiError.fromUnmappedFailure(response.status, envelope, correlationId);
    } catch (cause) {
      if (cause instanceof FleetApiError) {
        throw cause;
      }
      throw FleetApiError.fromUnmappedFailure(response.status, null, correlationId);
    }
  }

  // The service names the file in Content-Disposition; that name is preferred over a guess.
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename=("?)([^";]+)\1/i.exec(disposition);
  const fileName = match?.[2]?.trim() || fallbackFileName;

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoked on the next frame: revoking synchronously can cancel the download in some browsers.
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);

  return fileName;
}

export const apiClient = {
  get: <T>(path: string, query?: QueryParams, signal?: AbortSignal, service?: SflService) =>
    request<T>('GET', path, { query, signal, service }),

  /** POSTs that create state — carries an `Idempotency-Key` by default. */
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('POST', path, { ...options, body, idempotent: options?.idempotent ?? true }),

  /**
   * Multipart POST for file upload — the fuel CSV import is the only caller today.
   *
   * Same actor headers, same correlation id and the same envelope parsing as every other call; the
   * only difference is that the body is a `FormData` and the browser owns the Content-Type.
   */
  postForm: <T>(path: string, form: FormData, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('POST', path, { ...options, body: form, idempotent: options?.idempotent ?? false }),

  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('PATCH', path, { ...options, body }),

  /**
   * Replaces a value outright.
   *
   * Added for S152's runtime configuration, which supersedes a threshold rather than patching one —
   * PUT is the honest verb for "this key now has this value". Carries no idempotency key: the key
   * is in the path, so a repeat is the same write.
   */
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('PUT', path, { ...options, body }),

  /**
   * Removes a relationship.
   *
   * Added for S152's zone membership and readiness lock. Note what it is *not* used for: no S152
   * record is ever deleted — archival is the lifecycle state that retires one, because §21.2 of the
   * SRS protects examination-continuity records from deletion.
   */
  delete: <T>(path: string, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('DELETE', path, { ...options }),
};
