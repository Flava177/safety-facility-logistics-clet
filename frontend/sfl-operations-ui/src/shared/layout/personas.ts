import { actorRoles } from './programmes';

/**
 * Who this actor *is*, when a permission cannot tell you.
 *
 * ## Why permissions are not enough here
 *
 * Every other gate in this dashboard is a permission, and that is right: a screen should be offered
 * when the service will answer it. Personal portals break the pattern for a reason that is worth
 * stating, because otherwise somebody will "simplify" this away.
 *
 * `FLEET_DRIVER` holds eight permissions and **every one of them is also held by `FLEET_MANAGER`**.
 * So no permission distinguishes a driver from a manager, and gating "My driving day" on
 * `FUEL_LOGBOOK_CREATE` would put it in front of the fleet office as their landing page. The thing
 * that makes somebody a driver is not what they can do — it is what they *cannot*.
 *
 * ## The rule, and where it comes from
 *
 * A persona holds when the actor carries the persona's role **and no broader role in the same
 * programme**. That is not invented here: it is the rule the services already enforce, transcribed
 * so the two cannot disagree.
 *
 * - `FuelAccessPolicy.isDriverOnly` — `FLEET_DRIVER` present, and none of `FLEET_MANAGER`,
 *   `FLEET_LOGISTICS_OFFICER`, `SFL_ADMIN`.
 * - `FacilityFaultService.requesterFilter` and `BookingApplicationService.requesterFilter` —
 *   narrows only when `IFIMP_REQUESTER` is the actor's *only* facilities role, on the stated
 *   grounds that "a manager who also happens to hold the requester role is a manager; treating the
 *   union of roles as its narrowest member would make adding a role to somebody take capability
 *   away".
 *
 * The same sentence is the whole design of this file. A supervisor who is also on the driver rota
 * gets the fleet office, not a driver's day — and the service would return them the estate-wide data
 * anyway, so a personal portal would be lying about what it was showing.
 *
 * ## What this is not
 *
 * It is **not** an authorisation check. Nothing here hides data; the services do that, and they do
 * it per record. This decides which of several honest views to open on, and a wrong answer costs a
 * click rather than a disclosure.
 */

/** Resolved once at module load, like the rest of the actor context, so the sidebar cannot change
 * under a render. */
const has = (role: string): boolean => actorRoles.includes(role);

const hasNoneOf = (...broader: string[]): boolean =>
  !actorRoles.some((role) => broader.includes(role));

/** Every role that outranks a driver within FTLMP. */
const BROADER_THAN_DRIVER = ['FLEET_MANAGER', 'FLEET_LOGISTICS_OFFICER', 'SFL_ADMIN', 'DTI_ADMIN'];

/** Every role that outranks a requester within IFIMP. */
const BROADER_THAN_REQUESTER = [
  'FACILITIES_DIRECTOR',
  'FACILITIES_MANAGER',
  'IFIMP_MAINTENANCE_SUPERVISOR',
  'IFIMP_TECHNICIAN',
  'SFL_ADMIN',
  'DTI_ADMIN',
];

/** Every role that outranks a mailroom officer or centre manager within S171. */
const BROADER_THAN_DISPATCH_PERSONA = [
  'FLEET_MANAGER',
  'FLEET_LOGISTICS_OFFICER',
  'DISPATCH_CONTROLLER',
  'LOGISTICS_COORDINATOR',
  'SFL_ADMIN',
  'DTI_ADMIN',
];

export type PersonaCode =
  | 'driver'
  | 'requester'
  | 'technician'
  | 'mailroom'
  | 'centre'
  | 'assurance';

const predicates: Record<PersonaCode, () => boolean> = {
  /** Mirrors `FuelAccessPolicy.isDriverOnly`. */
  driver: () => has('FLEET_DRIVER') && hasNoneOf(...BROADER_THAN_DRIVER),

  /** Mirrors `FacilityFaultService.requesterFilter` and its booking twin. */
  requester: () => has('IFIMP_REQUESTER') && hasNoneOf(...BROADER_THAN_REQUESTER),

  /**
   * Both in-house and contract technicians, because S153 narrows both by assignment and the queue is
   * the same question — "what is mine today". A supervisor is excluded: they run the queue.
   */
  technician: () =>
    (has('IFIMP_TECHNICIAN') || has('VENDOR_TECHNICIAN'))
    && hasNoneOf('FACILITIES_DIRECTOR', 'FACILITIES_MANAGER', 'IFIMP_MAINTENANCE_SUPERVISOR', 'SFL_ADMIN', 'DTI_ADMIN'),

  mailroom: () => has('MAILROOM_OFFICER') && hasNoneOf(...BROADER_THAN_DISPATCH_PERSONA),

  centre: () => has('CENTRE_MANAGER') && hasNoneOf(...BROADER_THAN_DISPATCH_PERSONA),

  /**
   * Auditor and compliance, who are cross-programme by design (`crossProgrammeRoles`) and therefore
   * see every module — which is exactly why one consolidated assurance view beats four per-module
   * ones. Not excluded by admin roles: an administrator who is also an auditor still audits.
   */
  assurance: () => has('AUDITOR') || has('COMPLIANCE_OFFICER'),
};

/** Whether the current actor is this persona. */
export const isPersona = (persona: PersonaCode): boolean => predicates[persona]();
