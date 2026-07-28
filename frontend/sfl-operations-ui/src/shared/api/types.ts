/**
 * Wire types for the shared SFL service envelope.
 *
 * Every SFL service responds with `ApiResponse<T> { data, error }` (see
 * `gh.edu.clet.sfl.common.api.ApiResponse`). On a Bean Validation failure the fleet service puts
 * `FieldErrorResponse[]` into `data` while `error` carries `FLEET_VALIDATION_FAILED`; on a domain
 * failure `data` carries the exception's detail map. Both are modelled here.
 */

export interface ApiErrorEnvelope {
  code: string;
  message: string;
  correlationId: string | null;
  timestamp: string | null;
}

export interface ApiResponseEnvelope<T> {
  data: T | null;
  error: ApiErrorEnvelope | null;
}

/** One field-level validation failure — `FieldErrorResponse` on the service side. */
export interface FieldErrorPayload {
  field: string;
  message: string;
  rejectedValue: unknown;
}

/** Stable pagination envelope used by every fleet collection endpoint. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sort: string | null;
}

export const emptyPage = <T>(size = 20): PageResponse<T> => ({
  content: [],
  page: 0,
  size,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  sort: null,
});

/** Query parameter bag accepted by the client; `undefined`/`null` entries are dropped. */
export type QueryParams = Record<string, string | number | boolean | null | undefined>;

export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
}
