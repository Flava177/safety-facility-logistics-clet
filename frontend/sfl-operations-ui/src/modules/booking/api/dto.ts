import type { RecordMetadata } from 'modules/facilities/api/dto';
import type { LocationReadinessStatus, RecordLifecycleStatus, SpaceType } from 'modules/facilities/api/enums';
import type {
  ApprovalDecision,
  BookingPurpose,
  BookingStatus,
  ReadinessHoldReason,
  ResourceCategory,
  SetupTaskStatus,
} from './enums';

/**
 * S159 wire types, transcribed from `BookingResponses`.
 *
 * `RecordMetadata` is imported from S152 rather than redeclared: it is one record shape produced by
 * one `FacilitiesResponses.Metadata`, and a second copy here would be a second thing to keep in step
 * with a service that only has one.
 *
 * **Every derived field comes down the wire and none is recomputed.** `holdsTheSpace`, `occupiedFrom`
 * and `occupiedTo` are the ones that matter: the occupied window is the booking widened by its setup
 * and teardown buffers, and it is what the GIST exclusion constraint tests. A browser adding the
 * buffers itself would eventually disagree with the constraint, and the constraint is what decides
 * whether two lectures can have the same hall.
 */

export interface Booking {
  id: string;
  bookingReference: string;
  siteCode: string;
  roomId: string;
  roomCode: string | null;
  purpose: BookingPurpose;
  title: string;
  description: string | null;
  startsAt: string;
  endsAt: string;
  setupMinutes: number;
  teardownMinutes: number;
  /** The booked window widened by the buffers. What conflict is actually tested on. */
  occupiedFrom: string;
  occupiedTo: string;
  status: BookingStatus;
  /** Derived by the service from the status. Never inferred here. */
  holdsTheSpace: boolean;
  expectedAttendees: number;
  requestedBy: string;
  requestedFor: string | null;
  requestedAt: string;
  approvalRequired: boolean;
  approvalId: string | null;
  confirmedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  closureReason: string | null;
  /**
   * A flag beside the status, not a status.
   *
   * S159 decided this deliberately: a confirmed booking on a hall blocked on Tuesday is still a
   * confirmed booking somebody has in their diary, and moving it to an AT_RISK state would decide on
   * the estate's behalf that Tuesday's leak will still be there on Friday.
   */
  readinessHoldReason: ReadinessHoldReason | null;
  readinessHeldAt: string | null;
  overridden: boolean;
  overrideReason: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface BookingApproval {
  id: string;
  bookingId: string;
  decision: ApprovalDecision;
  reason: string | null;
  decidedBy: string;
  decidedAt: string;
}

export interface BookingAllocation {
  id: string;
  bookingId: string;
  resourceId: string;
  resourceCode: string | null;
  startsAt: string;
  endsAt: string;
  occupiedFrom: string;
  occupiedTo: string;
  quantity: number;
  /** True when the resource has a quantity of one, so the database enforces its exclusivity. */
  exclusive: boolean;
  released: boolean;
  allocatedBy: string;
  allocatedAt: string;
}

export interface BookableResource {
  id: string;
  siteCode: string;
  resourceCode: string;
  name: string;
  category: ResourceCategory;
  description: string | null;
  quantity: number;
  exclusive: boolean;
  homeRoomId: string | null;
  assetId: string | null;
  requiresSetup: boolean;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface SetupTask {
  id: string;
  bookingId: string;
  roomId: string;
  siteCode: string;
  description: string;
  dueBy: string | null;
  status: SetupTaskStatus;
  /** Decided by the service against its own clock, so two screens cannot disagree about it. */
  overdue: boolean;
  assignedTo: string | null;
  completedBy: string | null;
  completedAt: string | null;
  notes: string | null;
}

/**
 * One space and whether this window can have it.
 *
 * Unavailable spaces come back with a reason attached rather than filtered out. The question behind
 * "what is free at ten?" is usually "can I have Hall A at ten?", and a hall simply absent from the
 * list answers neither.
 */
export interface SpaceAvailability {
  roomId: string;
  roomCode: string;
  name: string | null;
  capacity: number | null;
  readinessStatus: LocationReadinessStatus;
  free: boolean;
  /** Free but for readiness — bookable by an actor holding `FACILITIES_BOOKING_OVERRIDE`. */
  availableWithOverride: boolean;
  readinessIssue: ReadinessHoldReason | null;
  readinessDetail: string | null;
  /** The bookings occupying this space in the requested window, when it is not free. */
  heldBy: Booking[];
}

export interface ResourceAvailability {
  resourceId: string;
  resourceCode: string;
  name: string;
  category: ResourceCategory;
  quantity: number;
  committed: number;
  free: number;
}

export interface BookingCounts {
  upcoming: number;
  awaitingApproval: number;
  onReadinessHold: number;
  recentNoShows: number;
}

// ---- request shapes -------------------------------------------------------------------------

export interface RequestBookingBody {
  roomId: string;
  purpose: BookingPurpose;
  title: string;
  description?: string | null;
  startsAt: string;
  endsAt: string;
  setupMinutes?: number | null;
  teardownMinutes?: number | null;
  expectedAttendees: number;
  requestedFor?: string | null;
  /** Resource id → quantity. Attached in the same transaction as the booking. */
  resources?: Record<string, number>;
  /** Honoured only when readiness would otherwise refuse and the actor may override. */
  overrideReason?: string | null;
}

export interface DecideBookingBody {
  approve: boolean;
  reason?: string | null;
  expectedVersion?: number | null;
}

export interface RescheduleBookingBody {
  startsAt: string;
  endsAt: string;
  setupMinutes?: number | null;
  teardownMinutes?: number | null;
  overrideReason?: string | null;
  expectedVersion?: number | null;
}

/** Start and complete share one body. Both are guarded by the version and the state machine. */
export interface TransitionBookingBody {
  notes?: string | null;
  expectedVersion?: number | null;
}

export interface CancelBookingBody {
  reason: string;
  expectedVersion?: number | null;
}

export interface RegisterResourceBody {
  siteCode: string;
  resourceCode: string;
  name: string;
  category: ResourceCategory;
  description?: string | null;
  quantity: number;
  homeRoomId?: string | null;
  assetId?: string | null;
  requiresSetup: boolean;
}

export interface UpdateResourceBody {
  name?: string | null;
  description?: string | null;
  quantity?: number | null;
  homeRoomId?: string | null;
  requiresSetup?: boolean | null;
  expectedVersion?: number | null;
}

export interface ChangeResourceLifecycleBody {
  lifecycleStatus: RecordLifecycleStatus;
  expectedVersion?: number | null;
}

export interface ResolveSetupTaskBody {
  /** `DONE` or `SKIPPED`. Skipping requires a reason in `notes`. */
  outcome: SetupTaskStatus;
  notes?: string | null;
}

export interface SpaceSearchParams {
  siteCode: string;
  from: string;
  to: string;
  purpose?: BookingPurpose;
  spaceType?: SpaceType;
  minimumCapacity?: number;
  setupMinutes?: number;
  teardownMinutes?: number;
}
