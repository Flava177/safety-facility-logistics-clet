/**
 * The programme model — which of the four SFL programmes a set of roles may see.
 *
 * Deliberately free of any import: no config, no `import.meta.env`, no React. That is what lets the
 * mapping be exercised directly rather than only through a running application, and the decision it
 * encodes — who sees what — is worth being able to check.
 *
 * Phase 1 is **13 systems under 4 programme modules, delivered as 5 services**, and those counts do
 * not line up. A programme is a user-facing grouping; a service is a deployment unit. See
 * `docs/architecture/microservices-realignment.md` for the map and ADR 0005 for the rule.
 *
 * Entitlement has **two grains**, and both are needed.
 *
 * - **Programme** answers "should this person see safety screens at all?" A head of fleet should not
 *   open their portal onto CCTV access management.
 * - **System** answers the question programme cannot: FTLMP is three systems in one deployable, so
 *   programme scoping alone shows a mailroom officer the whole fleet register and a driver the courier
 *   manifests. Neither can do anything with them, and the service says so on every call.
 *
 * The finer grain is derived from the same place as the coarser one — the roles the actor already
 * sends — because that is what IAM will carry as a claim.
 */

export type ProgrammeCode = 'IFIMP' | 'SSEMP' | 'FTLMP' | 'AVAMP';

export interface Programme {
  code: ProgrammeCode;
  /** What the programme is called in the shell. */
  label: string;
  /** The systems it covers, for the shell's own explanation of what a user is looking at. */
  scope: string;
}

export const programmes: Record<ProgrammeCode, Programme> = {
  IFIMP: {
    code: 'IFIMP',
    label: 'Facilities & Infrastructure',
    scope: 'Facility management, maintenance, room and resource booking',
  },
  SSEMP: {
    code: 'SSEMP',
    label: 'Safety, Security & Emergency',
    scope: 'Visitor, access, CCTV, intrusion, life safety, incidents and mass notification',
  },
  FTLMP: {
    code: 'FTLMP',
    label: 'Fleet, Transport & Logistics',
    scope: 'Fleet and vehicles, fuel and driver logbooks, courier and dispatch',
  },
  AVAMP: {
    code: 'AVAMP',
    label: 'Asset & Device Visibility',
    scope: 'Asset and device reference across every programme',
  },
};

export const allProgrammes = Object.keys(programmes) as ProgrammeCode[];

/**
 * The systems that have screens.
 *
 * Five of the thirteen. The others arrive with their own screens rather than being declared ahead of
 * them — a code here with nothing behind it would be a promise the sidebar cannot keep, and
 * {@link systemsFor} would happily entitle somebody to it.
 *
 * S152 is the first IFIMP system to arrive. Until it did, the programme was declared in
 * {@link programmes} and had no systems at all, so a facilities manager was entitled to a programme
 * that showed them nothing — an empty sidebar with no explanation.
 *
 * ## Why S153 is its own code even though no role's entitlement differs from S152
 *
 * Today every role entitled to S152 is also entitled to S153, and the reverse. That is not an
 * oversight and it is worth stating, because the obvious reading of two codes that always move
 * together is that one of them is redundant.
 *
 * It is here for three reasons. The C9 mapping lists S153 as a **Fast-Track system in its own
 * right**, the same status as S152, and the coverage claims in `solution.md` and ADR 0006 count
 * systems — "IFIMP: S152 and S153 complete" is only checkable if both exist. `VITE_SFL_SYSTEMS=S153`
 * becomes possible, which is how somebody looks at maintenance in isolation and is not possible
 * otherwise. And the no-entitlement page names the system: a user refused maintenance should read
 * "Maintenance management", not "Facility management".
 *
 * Entitlement is identical because `FacilitiesPermissionMatrix` puts `FACILITIES_FAULT_READ` and
 * `FACILITIES_WORK_ORDER_READ` inside its shared `READ_ONLY` set, so every facilities-facing role can
 * read maintenance. The day a role diverges, the code is already here to say so.
 */
export type SystemCode = 'S152' | 'S153' | 'S166' | 'S168' | 'S171' | 'S174';

export interface SflSystem {
  code: SystemCode;
  /** What the system is called where one has to be named to a user. */
  label: string;
  /** The programme it belongs to. A system is never in two. */
  programme: ProgrammeCode;
}

export const systems: Record<SystemCode, SflSystem> = {
  S152: { code: 'S152', label: 'Facility management', programme: 'IFIMP' },
  S153: { code: 'S153', label: 'Maintenance management', programme: 'IFIMP' },
  S166: { code: 'S166', label: 'Fleet & vehicle management', programme: 'FTLMP' },
  S168: { code: 'S168', label: 'Fuel & driver logbooks', programme: 'FTLMP' },
  S171: { code: 'S171', label: 'Courier & dispatch', programme: 'FTLMP' },
  S174: { code: 'S174', label: 'Emergency mass notification', programme: 'SSEMP' },
};

export const allSystems = Object.keys(systems) as SystemCode[];

/**
 * Roles that see every programme.
 *
 * Two kinds, both deliberate. **Platform administration** — `SFL_ADMIN`, `DTI_ADMIN` — is the
 * superadmin case the rule exists to make meaningful. **Cross-cutting oversight** — audit and
 * compliance — genuinely spans all four: an auditor who could see only one programme could not do
 * the job, and both roles read and export rather than operate, so breadth costs little.
 */
export const crossProgrammeRoles: readonly string[] = [
  'SFL_ADMIN',
  'DTI_ADMIN',
  'AUDITOR',
  'COMPLIANCE_OFFICER',
];

/**
 * Which programme each role belongs to.
 *
 * Transcribed from `SflRole` and the per-service permission matrices. A role absent from this map
 * grants no programme rather than defaulting to one — the same way the services drop a role name
 * they do not recognise instead of failing the request.
 *
 * This is the mapping IAM will eventually carry as a claim. Until then it is derived from the roles
 * the actor already sends, so there is no separate entitlement list to drift out of step.
 */
export const roleProgrammes: Record<string, ProgrammeCode[]> = {
  // SFL.IFIMP — facilities and infrastructure
  FACILITIES_DIRECTOR: ['IFIMP'],
  FACILITIES_MANAGER: ['IFIMP'],
  IFIMP_MAINTENANCE_SUPERVISOR: ['IFIMP'],
  IFIMP_TECHNICIAN: ['IFIMP'],
  IFIMP_REQUESTER: ['IFIMP'],
  VENDOR_TECHNICIAN: ['IFIMP'],

  // SFL.SSEMP — safety, security and emergency, including S174
  SECURITY_DIRECTOR: ['SSEMP'],
  SECURITY_OFFICER: ['SSEMP'],
  SOC_OPERATOR: ['SSEMP'],
  EMERGENCY_COORDINATOR: ['SSEMP'],
  HSE_MANAGER: ['SSEMP'],
  // Command sits over the emergency workflow — it approves activations and records after-action
  // approval — and over facility incident response with it.
  COMMAND_ROLE: ['SSEMP', 'IFIMP'],

  // SFL.FTLMP — fleet, transport and logistics
  FLEET_MANAGER: ['FTLMP'],
  FLEET_LOGISTICS_OFFICER: ['FTLMP'],
  FLEET_DRIVER: ['FTLMP'],
  FLEET_REPORTING_VIEWER: ['FTLMP'],
  DISPATCH_CONTROLLER: ['FTLMP'],
  LOGISTICS_COORDINATOR: ['FTLMP'],
  MAILROOM_OFFICER: ['FTLMP'],
  // A centre manager receives consignments and books the rooms they are for.
  CENTRE_MANAGER: ['FTLMP', 'IFIMP'],

  // SFL.AVAMP — the device reference layer, plus the technical roles that maintain the feeds
  // carrying device data into every programme.
  INTEGRATION_ENGINEER: ['AVAMP', 'IFIMP', 'SSEMP', 'FTLMP'],
  SERVICE_INTEGRATION: ['AVAMP', 'IFIMP', 'SSEMP', 'FTLMP'],
};

/**
 * Which systems each role can actually use.
 *
 * **Transcribed from the four permission matrices, not from judgement.** `FleetPermissionMatrix`,
 * `FuelPermissionMatrix`, `DispatchPermissionMatrix` and `EmergencyPermissionMatrix` each grant a set
 * of permissions per role; a role appears below for a system exactly when that system's matrix grants
 * it something. So the sidebar offers what the service will answer, and the two cannot disagree about
 * a role by accident.
 *
 * The interesting entries are the narrow ones, and none of them was invented:
 *
 * - `FLEET_DRIVER` is in the fleet and fuel matrices and **not** the dispatch one. A driver files a
 *   logbook; they do not run the mailroom.
 * - `MAILROOM_OFFICER`, `DISPATCH_CONTROLLER`, `LOGISTICS_COORDINATOR` and `CENTRE_MANAGER` are in the
 *   dispatch matrix only. A mailroom officer signs in to the mailroom.
 * - `SECURITY_OFFICER` is in the dispatch **and** emergency matrices — it can escalate a dispatch
 *   exception on a security-relevant consignment, which is a real permission the console used to hide.
 *
 * A role absent from this map is not narrowed; see {@link systemsFor} for why that is the right
 * default rather than the fail-closed one used for programmes.
 */
export const roleSystems: Record<string, SystemCode[]> = {
  // SFL.IFIMP — S152 is the facilities platform, S153 the maintenance module in the same service;
  // S159 will join them. Transcribed from `FacilitiesPermissionMatrix`: a role appears here exactly
  // when that matrix grants it something.
  //
  // `VENDOR_TECHNICIAN` is the one worth reading twice. A contractor holds work-order and evidence
  // permissions plus three estate *reads* — site, space and asset — and nothing else: no dashboard,
  // no readiness, not even `FACILITIES_FAULT_READ`. Both codes are granted because the estate reads
  // are what let a work order say where it is and what it is on; dropping S152 would break the link
  // from a job to the hall it is in. The sidebar then shows them exactly what they can read, which
  // is three registers and their own queue — the service narrows the queue itself, per record.
  FACILITIES_DIRECTOR: ['S152', 'S153'],
  FACILITIES_MANAGER: ['S152', 'S153'],
  IFIMP_MAINTENANCE_SUPERVISOR: ['S152', 'S153'],
  IFIMP_TECHNICIAN: ['S152', 'S153'],
  IFIMP_REQUESTER: ['S152', 'S153'],
  VENDOR_TECHNICIAN: ['S152', 'S153'],

  // SFL.FTLMP — all three systems live in `sfl-fleet-logistics-service`
  FLEET_MANAGER: ['S166', 'S168', 'S171'],
  FLEET_LOGISTICS_OFFICER: ['S166', 'S168', 'S171'],
  FLEET_REPORTING_VIEWER: ['S166', 'S168', 'S171'],
  FLEET_DRIVER: ['S166', 'S168'],
  DISPATCH_CONTROLLER: ['S171'],
  MAILROOM_OFFICER: ['S171'],
  LOGISTICS_COORDINATOR: ['S171'],
  // A centre manager receives consignments and declares their centre's operating mode, which is an
  // S152 permission the facilities matrix grants them.
  CENTRE_MANAGER: ['S152', 'S153', 'S171'],

  // SFL.SSEMP — S174 is its own deployable, split by ADR 0004
  EMERGENCY_COORDINATOR: ['S174'],
  SECURITY_DIRECTOR: ['S174'],
  SOC_OPERATOR: ['S174'],
  // An HSE manager reads the estate to place an incident and judge a location's standing.
  HSE_MANAGER: ['S152', 'S153', 'S174'],

  // Roles that span programmes at the system grain too
  SECURITY_OFFICER: ['S171', 'S174'],
  COMMAND_ROLE: ['S152', 'S153', 'S166', 'S168', 'S171', 'S174'],
  INTEGRATION_ENGINEER: ['S152', 'S153', 'S166', 'S168', 'S171', 'S174'],
  SERVICE_INTEGRATION: ['S152', 'S153', 'S166', 'S168', 'S171'],
};

/** Splits a comma-separated header or env value into normalised role names. */
export const parseList = (value: string): string[] =>
  value
    .split(',')
    .map((entry) => entry.trim().toUpperCase())
    .filter(Boolean);

/** `true` when any role sees every programme — the manager and superadmin case. */
export const isCrossProgramme = (roles: string[]): boolean =>
  roles.some((role) => crossProgrammeRoles.includes(role));

/**
 * The programmes a set of roles may see, in declared order.
 *
 * An unrecognised role contributes nothing rather than everything, so an actor with no known role
 * sees nothing. That is the safer default to *read*: an empty sidebar is a question somebody asks,
 * where a full one is not.
 *
 * **A role's systems imply their programmes**, and that union is not a convenience — it is what stops
 * the two maps drifting. Adding a system to {@link roleSystems} entitles the role to that system's
 * programme automatically, so the pair cannot fall out of step the way they had:
 *
 * - `COMMAND_ROLE` holds permissions in the fleet, fuel and dispatch matrices, and
 *   {@link roleProgrammes} listed only IFIMP and SSEMP. The whole FTLMP side of the console was hidden
 *   from a role that could operate it.
 * - `SECURITY_OFFICER` can read dispatch items and manifests and escalate a dispatch exception, and
 *   was listed as SSEMP alone — so it could never see the consignment it was meant to escalate.
 *
 * Both close here by construction rather than by being edited into a second list.
 */
export const programmesFor = (roles: string[]): ProgrammeCode[] => {
  if (isCrossProgramme(roles)) {
    return [...allProgrammes];
  }
  const entitled = new Set<ProgrammeCode>();
  roles.forEach((role) => {
    (roleProgrammes[role] ?? []).forEach((code) => entitled.add(code));
    (roleSystems[role] ?? []).forEach((system) => entitled.add(systems[system].programme));
  });
  return allProgrammes.filter((code) => entitled.has(code));
};

/**
 * The systems a set of roles may see, in declared order.
 *
 * **The default is the opposite of {@link programmesFor}'s, on purpose.** A role absent from
 * {@link roleSystems} is *not* narrowed: the actor gets every system of every programme they are
 * entitled to. Only an actor whose roles say something about systems is narrowed to what they say.
 *
 * Fail-closed is right for programmes, where the question is "should this person be in safety at all"
 * and silence should mean no. It is wrong here. A new FTLMP role added to {@link roleProgrammes} and
 * forgotten in {@link roleSystems} would otherwise produce a console that is entitled to a programme
 * and shows none of it — an empty sidebar with no explanation, which reads as a broken build rather
 * than as a permission. Widening to the programme keeps the failure legible, and the services refuse
 * anything the role cannot do regardless.
 */
export const systemsFor = (roles: string[]): SystemCode[] => {
  if (isCrossProgramme(roles)) {
    return [...allSystems];
  }
  const declared = new Set<SystemCode>();
  roles.forEach((role) => {
    (roleSystems[role] ?? []).forEach((code) => declared.add(code));
  });
  if (declared.size > 0) {
    return allSystems.filter((code) => declared.has(code));
  }
  // No role said anything about systems, so nothing is narrowed — see above.
  const entitledProgrammes = programmesFor(roles);
  return allSystems.filter((code) => entitledProgrammes.includes(systems[code].programme));
};

/**
 * What to call the portal an actor is looking at.
 *
 * One programme names itself. Several means the actor spans programmes, and the shell says so
 * rather than picking one and misrepresenting the other.
 */
export const portalLabelFor = (entitled: ProgrammeCode[]): string => {
  // Whoever reaches all four sees the same thing, so they are told the same thing. An integration
  // engineer arrives here by a different route from a superadmin; that distinction is real in the
  // permission matrices and meaningless in a sidebar caption.
  if (entitled.length === allProgrammes.length) {
    return 'All programmes';
  }
  if (entitled.length === 1) {
    return programmes[entitled[0]].label;
  }
  if (entitled.length === 0) {
    return 'No programme assigned';
  }
  return `${entitled.length} programmes`;
};
