/**
 * The seeded accounts a person can sign in as.
 *
 * <h2>What this is, stated plainly</h2>
 *
 * A **development sign-in**. It checks the email against the list below and the password against one
 * shared constant, then makes that account the actor for the session. It is not authentication: no
 * token is issued, nothing is verified against an identity provider, and the credentials are in the
 * bundle that is served to the browser.
 *
 * That is a deliberate and bounded choice. The services this dashboard talks to run locally with
 * `SFL_SECURITY_ENABLED=false`, where the actor is whatever the `X-SFL-*` headers claim — so a login
 * page here can only ever decide *which headers to send*. Making it look like more than that would be
 * the worse outcome: a form that appears to authenticate while the service behind it is open.
 *
 * <h2>The real path already exists beside it</h2>
 *
 * `deploy/keycloak/sfl-realm.json` carries the same twenty-two accounts with the same addresses and
 * password, and `keycloak.ts` exchanges them for a genuine token. When a service runs with security
 * on, that is the path — and `session.ts` stores either kind identically, so nothing downstream cares
 * which one signed you in. The roles below and the roles in the realm are the same roles.
 *
 * <h2>Why the role list is here rather than fetched</h2>
 *
 * Because the sign-in has to work before any call is made. Every role name below is an `SflRole` the
 * services already know; if one were wrong the account would sign in and then be refused everything,
 * which is why `accounts.test.ts` checks each against `roleProgrammes`/`roleSystems`.
 */

export interface SeededAccount {
  email: string;
  /** Sent as `X-SFL-User` and shown in the account panel. */
  username: string;
  displayName: string;
  roles: string[];
  sites: string[];
  /** What this account is for, shown on the sign-in page's account list. */
  description: string;
}

/** One password for every seeded account. Development only — see the docblock. */
export const SEEDED_PASSWORD = 'Password@Clet1';

export const seededAccounts: SeededAccount[] = [
  // ── FTLMP ────────────────────────────────────────────────────────────────────────────────────
  {
    email: 'fleetmanager@clet.gh',
    username: 'fleet.manager',
    displayName: 'Fleet Manager',
    roles: ['FLEET_MANAGER'],
    sites: ['CLET-HQ'],
    description: 'Fleet, fuel and dispatch — plans trips and assigns drivers',
  },
  {
    email: 'driver@clet.gh',
    username: 'kwame.driver',
    displayName: 'Kwame Driver',
    roles: ['FLEET_DRIVER'],
    sites: ['CLET-HQ'],
    description: 'My driving day, assigned trips, logbook',
  },
  {
    email: 'fleetofficer@clet.gh',
    username: 'fleet.officer',
    displayName: 'Fleet Officer',
    roles: ['FLEET_LOGISTICS_OFFICER'],
    sites: ['CLET-HQ'],
    description: 'Fleet, fuel and dispatch operations',
  },
  {
    email: 'reportingviewer@clet.gh',
    username: 'reporting.viewer',
    displayName: 'Reporting Viewer',
    roles: ['FLEET_REPORTING_VIEWER'],
    sites: ['CLET-HQ'],
    description: 'Fleet, fuel and dispatch — read only',
  },
  {
    email: 'dispatchcontroller@clet.gh',
    username: 'dispatch.controller',
    displayName: 'Dispatch Controller',
    roles: ['DISPATCH_CONTROLLER'],
    sites: ['CLET-HQ'],
    description: 'Courier items, manifests, custody and exceptions',
  },
  {
    email: 'logisticscoordinator@clet.gh',
    username: 'logistics.coordinator',
    displayName: 'Logistics Coordinator',
    roles: ['LOGISTICS_COORDINATOR'],
    sites: ['CLET-HQ'],
    description: 'Courier and dispatch coordination',
  },
  {
    email: 'mailroomofficer@clet.gh',
    username: 'ama.mailroom',
    displayName: 'Ama Mailroom',
    roles: ['MAILROOM_OFFICER'],
    sites: ['CLET-HQ'],
    description: 'Inbound registration and today’s distribution',
  },
  {
    email: 'centremanager@clet.gh',
    username: 'adjoa.centre',
    displayName: 'Adjoa Centre',
    roles: ['CENTRE_MANAGER'],
    sites: ['CLET-HQ'],
    description: 'Centre receipts, examination mode, room booking',
  },

  // ── IFIMP ────────────────────────────────────────────────────────────────────────────────────
  {
    email: 'facilitiesmanager@clet.gh',
    username: 'facilities.manager',
    displayName: 'Facilities Manager',
    roles: ['FACILITIES_MANAGER'],
    sites: ['CLET-HQ'],
    description: 'Estate, readiness, maintenance and booking',
  },
  {
    email: 'facilitiesdirector@clet.gh',
    username: 'facilities.director',
    displayName: 'Facilities Director',
    roles: ['FACILITIES_DIRECTOR'],
    sites: ['*'],
    description: 'All of IFIMP, every site, including overrides',
  },
  {
    email: 'maintenancesupervisor@clet.gh',
    username: 'maintenance.supervisor',
    displayName: 'Maintenance Supervisor',
    roles: ['IFIMP_MAINTENANCE_SUPERVISOR'],
    sites: ['CLET-HQ'],
    description: 'Work-order queue, readiness overrides, schedules',
  },
  {
    email: 'technician@clet.gh',
    username: 'yaw.technician',
    displayName: 'Yaw Technician',
    roles: ['IFIMP_TECHNICIAN'],
    sites: ['CLET-HQ'],
    description: 'My work queue and room turnaround',
  },
  {
    email: 'vendortechnician@clet.gh',
    username: 'kofi.vendor',
    displayName: 'Kofi Vendor',
    roles: ['VENDOR_TECHNICIAN'],
    sites: ['CLET-HQ'],
    description: 'Only the work orders assigned to this contractor',
  },
  {
    email: 'requester@clet.gh',
    username: 'akosua.requester',
    displayName: 'Akosua Requester',
    roles: ['IFIMP_REQUESTER'],
    sites: ['CLET-HQ'],
    description: 'My requests and room booking',
  },

  // ── SSEMP ────────────────────────────────────────────────────────────────────────────────────
  {
    email: 'emergencycoordinator@clet.gh',
    username: 'emergency.coordinator',
    displayName: 'Emergency Coordinator',
    roles: ['EMERGENCY_COORDINATOR'],
    sites: ['CLET-HQ'],
    description: 'Activations, break-glass, drills',
  },
  {
    email: 'securityofficer@clet.gh',
    username: 'security.officer',
    displayName: 'Security Officer',
    roles: ['SECURITY_OFFICER'],
    sites: ['CLET-HQ'],
    description: 'Dispatch exceptions and emergency notification',
  },
  {
    email: 'socoperator@clet.gh',
    username: 'soc.operator',
    displayName: 'SOC Operator',
    roles: ['SOC_OPERATOR'],
    sites: ['CLET-HQ'],
    description: 'Emergency notification monitoring',
  },
  {
    email: 'hsemanager@clet.gh',
    username: 'hse.manager',
    displayName: 'HSE Manager',
    roles: ['HSE_MANAGER'],
    sites: ['CLET-HQ'],
    description: 'Estate readiness and emergency notification',
  },

  // ── Cross-programme ──────────────────────────────────────────────────────────────────────────
  {
    email: 'command@clet.gh',
    username: 'command.role',
    displayName: 'Command',
    roles: ['COMMAND_ROLE'],
    sites: ['*'],
    description: 'Oversight across every programme and site',
  },
  {
    email: 'auditor@clet.gh',
    username: 'nana.auditor',
    displayName: 'Nana Auditor',
    roles: ['AUDITOR'],
    sites: ['*'],
    description: 'Read and prove — audit, evidence, chain replay',
  },
  {
    email: 'complianceofficer@clet.gh',
    username: 'esi.compliance',
    displayName: 'Esi Compliance',
    roles: ['COMPLIANCE_OFFICER'],
    sites: ['*'],
    description: 'The auditor view plus export approval',
  },
  {
    email: 'sfladmin@clet.gh',
    username: 'sfl.admin',
    displayName: 'SFL Administrator',
    roles: ['SFL_ADMIN'],
    sites: ['*'],
    description: 'Every programme, every system, every site',
  },
];

/** Case-insensitive, because nobody types an email address the way it is stored. */
export const findAccount = (email: string): SeededAccount | undefined => {
  const wanted = email.trim().toLowerCase();
  return seededAccounts.find((account) => account.email.toLowerCase() === wanted);
};
