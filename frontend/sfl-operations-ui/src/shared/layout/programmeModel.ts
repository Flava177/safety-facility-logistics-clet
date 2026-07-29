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
 */
export const programmesFor = (roles: string[]): ProgrammeCode[] => {
  if (isCrossProgramme(roles)) {
    return [...allProgrammes];
  }
  const entitled = new Set<ProgrammeCode>();
  roles.forEach((role) => {
    (roleProgrammes[role] ?? []).forEach((code) => entitled.add(code));
  });
  return allProgrammes.filter((code) => entitled.has(code));
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
