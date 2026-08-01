import { apiClient } from 'shared/api/client';
import type { QueryParams } from 'shared/api/types';
import type {
  BookableResource,
  Booking,
  BookingAllocation,
  BookingApproval,
  BookingCounts,
  CancelBookingBody,
  ChangeResourceLifecycleBody,
  DecideBookingBody,
  RegisterResourceBody,
  RequestBookingBody,
  RescheduleBookingBody,
  ResolveSetupTaskBody,
  ResourceAvailability,
  SetupTask,
  SpaceAvailability,
  SpaceSearchParams,
  TransitionBookingBody,
  UpdateResourceBody,
} from './dto';
import type { BookingPurpose, BookingStatus, ResourceCategory } from './enums';

/**
 * S159 room and resource booking — the paths that had no client until now.
 *
 * Same service and same envelope as S152 and S153, so this shares their transport rather than
 * reaching for its own. `idempotent: true` is set on the two state-**creating** POSTs that accept an
 * `Idempotency-Key`, and deliberately not on the PATCH transitions: each is guarded by the record's
 * version and its state machine, so a repeat is either a no-op or an invalid-transition error, and a
 * key would be ceremony with no failure mode behind it.
 */

const base = '/api/v1/facilities';
const service = 'facilities' as const;

const get = <T>(path: string, query?: QueryParams, signal?: AbortSignal) =>
  apiClient.get<T>(`${base}${path}`, query, signal, service);

const post = <T>(path: string, body?: unknown, idempotent = false) =>
  apiClient.post<T>(`${base}${path}`, body, { service, idempotent });

const patch = <T>(path: string, body?: unknown) =>
  apiClient.patch<T>(`${base}${path}`, body, { service });

export interface BookingSearchParams {
  siteCode?: string;
  roomId?: string;
  status?: BookingStatus;
  purpose?: BookingPurpose;
  requestedBy?: string;
  from?: string;
  to?: string;
  /** Only the bookings that currently hold their space. */
  liveOnly?: boolean;
  onReadinessHold?: boolean;
  limit?: number;
}

export const bookingsApi = {
  /**
   * The diary.
   *
   * An actor holding only `IFIMP_REQUESTER` receives their own bookings and no others, narrowed per
   * record by `BookingApplicationService.requesterFilter` — this asks for the register and gets a
   * shorter one. There is no client-side filter and there must never be: a filter in the browser is a
   * display convention, and the rows would still have crossed the boundary.
   */
  search: (params: BookingSearchParams, signal?: AbortSignal) =>
    get<Booking[]>('/bookings', params as QueryParams, signal),

  findById: (bookingId: string, signal?: AbortSignal) =>
    get<Booking>(`/bookings/${bookingId}`, undefined, signal),

  counts: (siteCode: string, signal?: AbortSignal) =>
    get<BookingCounts>('/bookings/counts', { siteCode }, signal),

  /**
   * The decisions taken on a booking.
   *
   * Empty for a booking that needed none, which is what says so — there is no separate flag to fall
   * out of step with the record.
   */
  approvals: (bookingId: string, signal?: AbortSignal) =>
    get<BookingApproval[]>(`/bookings/${bookingId}/approvals`, undefined, signal),

  allocations: (bookingId: string, signal?: AbortSignal) =>
    get<BookingAllocation[]>(`/bookings/${bookingId}/resources`, undefined, signal),

  setupTasks: (bookingId: string, signal?: AbortSignal) =>
    get<SetupTask[]>(`/bookings/${bookingId}/setup-tasks`, undefined, signal),

  /** The one state-creating POST on a booking, and the only booking call that takes a key. */
  request: (body: RequestBookingBody) => post<Booking>('/bookings', body, true),

  decide: (bookingId: string, body: DecideBookingBody) =>
    patch<Booking>(`/bookings/${bookingId}/decision`, body),

  reschedule: (bookingId: string, body: RescheduleBookingBody) =>
    patch<Booking>(`/bookings/${bookingId}/schedule`, body),

  start: (bookingId: string, body: TransitionBookingBody) =>
    patch<Booking>(`/bookings/${bookingId}/start`, body),

  complete: (bookingId: string, body: TransitionBookingBody) =>
    patch<Booking>(`/bookings/${bookingId}/completion`, body),

  /**
   * Cancel.
   *
   * `IFIMP_REQUESTER` deliberately does not hold `FACILITIES_BOOKING_CANCEL`, and that is not a
   * missing capability: `requireMayAct` lets an actor act on a booking they requested. The permission
   * gates acting on somebody else's.
   */
  cancel: (bookingId: string, body: CancelBookingBody) =>
    patch<Booking>(`/bookings/${bookingId}/cancellation`, body),

  allocate: (bookingId: string, resources: Record<string, number>) =>
    post<BookingAllocation[]>(`/bookings/${bookingId}/resources`, { resources }),

  releaseAllocation: (bookingId: string, allocationId: string) =>
    apiClient.delete<void>(`${base}/bookings/${bookingId}/resources/${allocationId}`, { service }),
};

export interface ResourceAvailabilityParams {
  siteCode: string;
  from: string;
  to: string;
  category?: ResourceCategory;
  setupMinutes?: number;
  teardownMinutes?: number;
}

export const availabilityApi = {
  /**
   * Which spaces can take this window.
   *
   * Asked with the same buffers the booking will carry, because that is what the exclusion constraint
   * tests: a hall that looks free for a two-hour examination is not free once thirty minutes of layout
   * change are added at each end. A browser filtering rooms by its own idea of a clash would disagree
   * with the database the moment two people asked at once, and the database is what decides.
   *
   * **Nothing is reserved by asking.** Two people can both be told Hall A is free and both request it;
   * the first wins and the second is refused with `BOOKING_CONFLICT`.
   */
  spaces: (params: SpaceSearchParams, signal?: AbortSignal) =>
    get<SpaceAvailability[]>('/booking-availability/spaces', params as unknown as QueryParams, signal),

  resources: (params: ResourceAvailabilityParams, signal?: AbortSignal) =>
    get<ResourceAvailability[]>(
      '/booking-availability/resources',
      params as unknown as QueryParams,
      signal,
    ),

  calendar: (params: { roomId: string; from: string; to: string }, signal?: AbortSignal) =>
    get<Booking[]>('/booking-availability/calendar', params as QueryParams, signal),
};

export const bookableResourcesApi = {
  search: (params: { siteCode?: string; category?: ResourceCategory }, signal?: AbortSignal) =>
    get<BookableResource[]>('/bookable-resources', params as QueryParams, signal),

  findById: (resourceId: string, signal?: AbortSignal) =>
    get<BookableResource>(`/bookable-resources/${resourceId}`, undefined, signal),

  register: (body: RegisterResourceBody) =>
    post<BookableResource>('/bookable-resources', body, true),

  update: (resourceId: string, body: UpdateResourceBody) =>
    patch<BookableResource>(`/bookable-resources/${resourceId}`, body),

  changeLifecycle: (resourceId: string, body: ChangeResourceLifecycleBody) =>
    patch<BookableResource>(`/bookable-resources/${resourceId}/lifecycle`, body),
};

export const setupTasksApi = {
  /** Ordered by when the room is needed, not when the task was raised. Defaults to the next two days. */
  queue: (params: { siteCode?: string; dueBefore?: string; limit?: number }, signal?: AbortSignal) =>
    get<SetupTask[]>('/setup-tasks', params as QueryParams, signal),

  /**
   * Resolve a setup task.
   *
   * Setup tasks are deliberately **not** S153 work orders. Routing them there would buy the queue, the
   * SLA and the closure evidence for free — and would put a twenty-minute chair rearrangement in the
   * same queue as a failed standby generator, where the generator ends up on page four.
   */
  resolve: (taskId: string, body: ResolveSetupTaskBody) =>
    patch<SetupTask>(`/setup-tasks/${taskId}/resolution`, body),
};
