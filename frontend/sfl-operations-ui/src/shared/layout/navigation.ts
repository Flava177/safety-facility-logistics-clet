import { IconName } from 'shared/components/Icon';
import { ProgrammeCode, SystemCode, entitledTo, entitledToSystem } from './programmes';

/**
 * Dashboard navigation.
 *
 * Only destinations that are built and wired to a service appear here. Modules that do not exist
 * yet are not listed at all — a greyed-out "coming soon" entry costs an operator a click to
 * discover nothing, and it makes a working dashboard look half-finished.
 *
 * **Every section declares the programme it belongs to**, and the shell renders only the sections
 * the actor is entitled to. Phase 1 is 13 systems under 4 programme modules delivered as 5
 * services, and those counts do not line up — a programme is a user-facing grouping, a service is
 * a deployment unit. See `programmes.ts` and ADR 0005.
 *
 * The programme is a property of the **system**, not of the service it happens to ship in. S174 is
 * its own deployable (ADR 0004, for availability and blast radius) and is still SSEMP here, sitting
 * beside three FTLMP sections in one bundle. That a user cannot tell which service serves what is
 * the point: deployment topology must not be inferable from a sidebar.
 */

export interface NavItem {
  label: string;
  to: string;
  icon: IconName;
  /** Matches child routes too — `/fleet/vehicles/42` still highlights "Vehicle register". */
  matchPrefix?: string;
  description?: string;
}

export interface NavSection {
  heading: string;
  /** Which of the four SFL programmes this section belongs to. Drives what a user sees. */
  programme: ProgrammeCode;
  /**
   * Which system it belongs to — the finer half of the same decision.
   *
   * Programme alone is not enough inside FTLMP, where three systems share one deployable: without
   * this, a mailroom officer sees the fleet register and a driver sees the courier manifests.
   */
  system: SystemCode;
  items: NavItem[];
}

export const fleetPaths = {
  dashboard: '/fleet',
  vehicles: '/fleet/vehicles',
  vehicleDetail: (vehicleId: string) => `/fleet/vehicles/${vehicleId}`,
  drivers: '/fleet/drivers',
  driverDetail: (driverId: string) => `/fleet/drivers/${driverId}`,
  trips: '/fleet/trips',
  tripDetail: (tripId: string) => `/fleet/trips/${tripId}`,
  workflow: '/fleet/workflow',
  workflowDetail: (itemId: string) => `/fleet/workflow/${itemId}`,
  compliance: '/fleet/compliance',
  governance: '/fleet/governance',
  integrations: '/fleet/integrations',
};

/**
 * S168 fuel routes.
 *
 * Policy detail has no endpoint of its own (`GET /policies/{id}` does not exist), so the screen
 * selects out of the site's policy list — the route still exists because a policy is a record an
 * operator links to and comes back to.
 */
export const fuelPaths = {
  dashboard: '/fuel',
  transactions: '/fuel/transactions',
  transactionDetail: (transactionId: string) => `/fuel/transactions/${transactionId}`,
  logbooks: '/fuel/logbooks',
  logbookDetail: (logbookId: string) => `/fuel/logbooks/${logbookId}`,
  reconciliation: '/fuel/reconciliation',
  anomalies: '/fuel/anomalies',
  anomalyDetail: (anomalyId: string) => `/fuel/anomalies/${anomalyId}`,
  imports: '/fuel/imports',
  policies: '/fuel/policies',
  policyDetail: (policyId: string) => `/fuel/policies/${policyId}`,
  integrations: '/fuel/integrations',
};

/**
 * S171 dispatch routes.
 *
 * Custody, receipts and the return leg have no register of their own: each belongs to one
 * consignment and is only meaningful beside it, so they live on the manifest detail screen rather
 * than as three more sidebar entries an operator would have to cross-reference by hand.
 */
export const dispatchPaths = {
  dashboard: '/dispatch',
  items: '/dispatch/items',
  itemDetail: (itemId: string) => `/dispatch/items/${itemId}`,
  manifests: '/dispatch/manifests',
  manifestDetail: (manifestId: string) => `/dispatch/manifests/${manifestId}`,
  inbound: '/dispatch/inbound',
  exceptions: '/dispatch/exceptions',
  exceptionDetail: (caseId: string) => `/dispatch/exceptions/${caseId}`,
  scans: '/dispatch/scans',
  integrations: '/dispatch/integrations',
};

/**
 * S174 emergency notification routes.
 *
 * Break-glass is a destination rather than a mode on the compose dialog. It is a different
 * authorisation, it creates a different obligation, and in a declared emergency it has to be one
 * click from anywhere — a screen that is both a warning and the shortest path is what that needs.
 *
 * Templates and scenarios share a screen, and so do audience groups and recipient zones: each pair
 * answers one question between them and is chosen together on every activation. Only the template
 * has a detail route, because `GET /templates/{id}` is the only detail endpoint this service has.
 */
export const emergencyPaths = {
  dashboard: '/emergency',
  activations: '/emergency/activations',
  activationDetail: (activationId: string) => `/emergency/activations/${activationId}`,
  breakGlass: '/emergency/break-glass',
  templates: '/emergency/templates',
  templateDetail: (templateId: string) => `/emergency/templates/${templateId}`,
  audiences: '/emergency/audiences',
  drills: '/emergency/drills',
  integrations: '/emergency/integrations',
};

export const navSections: NavSection[] = [
  {
    heading: 'Operations',
    programme: 'FTLMP',
    system: 'S166',
    items: [
      {
        label: 'Dashboard',
        to: fleetPaths.dashboard,
        icon: 'dashboard',
        description: 'Readiness, activity and exceptions',
      },
      {
        label: 'Trips & assignments',
        to: fleetPaths.trips,
        icon: 'route',
        matchPrefix: fleetPaths.trips,
        description: 'Plan, assign, start and close movements',
      },
      {
        label: 'Workflow queue',
        to: fleetPaths.workflow,
        icon: 'workflow',
        matchPrefix: fleetPaths.workflow,
        description: 'Inspections, defects and escalations',
      },
    ],
  },
  {
    heading: 'Registers',
    programme: 'FTLMP',
    system: 'S166',
    items: [
      {
        label: 'Vehicle register',
        to: fleetPaths.vehicles,
        icon: 'truck',
        matchPrefix: fleetPaths.vehicles,
        description: 'Fleet inventory and readiness',
      },
      {
        label: 'Driver register',
        to: fleetPaths.drivers,
        icon: 'driver',
        matchPrefix: fleetPaths.drivers,
        description: 'Licence standing and eligibility',
      },
    ],
  },
  {
    heading: 'Assurance',
    programme: 'FTLMP',
    system: 'S166',
    items: [
      {
        label: 'Compliance & service',
        to: fleetPaths.compliance,
        icon: 'shield-check',
        description: 'Documents, servicing and expiry',
      },
      {
        label: 'Evidence & audit',
        to: fleetPaths.governance,
        icon: 'document',
        description: 'Closure evidence and audit trail',
      },
      {
        label: 'Integration health',
        to: fleetPaths.integrations,
        icon: 'cloud',
        description: 'Inbound and outbound message flow',
      },
    ],
  },
  {
    heading: 'Fuel & driver logbooks',
    programme: 'FTLMP',
    system: 'S168',
    items: [
      {
        label: 'Fuel dashboard',
        to: fuelPaths.dashboard,
        icon: 'fuel',
        description: 'Spend, volume and reconciliation standing',
      },
      {
        label: 'Fuel transactions',
        to: fuelPaths.transactions,
        icon: 'coins',
        matchPrefix: fuelPaths.transactions,
        description: 'Captured, imported and provider transactions',
      },
      {
        label: 'Driver logbooks',
        to: fuelPaths.logbooks,
        icon: 'book',
        matchPrefix: fuelPaths.logbooks,
        description: 'Journey records through review to approval',
      },
      {
        label: 'Reconciliation',
        to: fuelPaths.reconciliation,
        icon: 'scale',
        description: 'Run the policy rules and read the outcome',
      },
      {
        label: 'Anomaly cases',
        to: fuelPaths.anomalies,
        icon: 'alert-triangle',
        matchPrefix: fuelPaths.anomalies,
        description: 'Exception queue, explanation and closure',
      },
      {
        label: 'CSV imports',
        to: fuelPaths.imports,
        icon: 'upload',
        description: 'Bulk capture with row-level outcomes',
      },
      {
        label: 'Fuel policies',
        to: fuelPaths.policies,
        icon: 'shield-check',
        matchPrefix: fuelPaths.policies,
        description: 'Effective-dated limits the rules are read from',
      },
      {
        label: 'Provider integration',
        to: fuelPaths.integrations,
        icon: 'cloud',
        description: 'Provider ingest and outbound publication',
      },
    ],
  },
  {
    heading: 'Courier & dispatch',
    programme: 'FTLMP',
    system: 'S171',
    items: [
      {
        label: 'Dispatch dashboard',
        to: dispatchPaths.dashboard,
        icon: 'dashboard',
        description: 'Consignments in transit and open exceptions',
      },
      {
        label: 'Courier items',
        to: dispatchPaths.items,
        icon: 'package',
        matchPrefix: dispatchPaths.items,
        description: 'Every tracked item, inbound and outbound',
      },
      {
        label: 'Manifests',
        to: dispatchPaths.manifests,
        icon: 'clipboard-list',
        matchPrefix: dispatchPaths.manifests,
        description: 'Seals, custody, receipt and the return leg',
      },
      {
        label: 'Inbound mail',
        to: dispatchPaths.inbound,
        icon: 'inbox',
        description: 'Registration and acknowledged distribution',
      },
      {
        label: 'Exception cases',
        to: dispatchPaths.exceptions,
        icon: 'alert-triangle',
        matchPrefix: dispatchPaths.exceptions,
        description: 'Custody gaps, variances and discrepancies',
      },
      {
        label: 'Scan imports',
        to: dispatchPaths.scans,
        icon: 'upload',
        description: 'Scanner batches and per-row outcomes',
      },
      {
        label: 'Scanner integration',
        to: dispatchPaths.integrations,
        icon: 'cloud',
        description: 'Scanner and carrier feeds, outbound publication',
      },
    ],
  },
  {
    // SSEMP, not FTLMP. S174 is its own deployable service but it belongs to the safety,
    // security and emergency programme — so a fleet operator does not see it, and a SOC
    // operator or emergency coordinator does. ADR 0005.
    heading: 'Emergency notifications',
    programme: 'SSEMP',
    system: 'S174',
    items: [
      {
        label: 'Emergency dashboard',
        to: emergencyPaths.dashboard,
        icon: 'siren',
        description: 'Live broadcasts and outstanding obligations',
      },
      {
        label: 'Activations',
        to: emergencyPaths.activations,
        icon: 'megaphone',
        matchPrefix: emergencyPaths.activations,
        description: 'Compose, approve, send, stand down and close',
      },
      {
        label: 'Break glass',
        to: emergencyPaths.breakGlass,
        icon: 'zap',
        description: 'Declared-emergency send with no approval',
      },
      {
        label: 'Templates & scenarios',
        to: emergencyPaths.templates,
        icon: 'document',
        matchPrefix: emergencyPaths.templates,
        description: 'What a broadcast says, and what cites it',
      },
      {
        label: 'Audiences & zones',
        to: emergencyPaths.audiences,
        icon: 'users',
        description: 'Who a broadcast reaches, and where',
      },
      {
        label: 'Drills',
        to: emergencyPaths.drills,
        icon: 'target',
        description: 'Rehearsals and notification performance',
      },
      {
        label: 'Provider integration',
        to: emergencyPaths.integrations,
        icon: 'cloud',
        description: 'Outbound publication and the callback path',
      },
    ],
  },
];

/**
 * How the application names itself.
 *
 * `module` is no longer fixed: which programme an operator is looking at depends on what they are
 * entitled to, so the shell reads it from `portalLabel()` rather than from a constant that was only
 * ever true for a fleet user.
 */
export const directorate = {
  name: 'Safety, Facilities & Logistics',
  shortName: 'SFL Operations',
  parentOrganisation: 'CLET',
};

/** The navigation sections this actor is entitled to, in declared order. */
export const entitledSections = (): NavSection[] =>
  navSections.filter(
    (section) => entitledTo(section.programme) && entitledToSystem(section.system),
  );

/**
 * Where an actor lands when they open the application.
 *
 * The first destination of the first programme they are entitled to — **not** the fleet dashboard,
 * which is only the right answer for a fleet user. `null` when they are entitled to nothing, which
 * the router turns into an explanation rather than a redirect loop.
 */
export const landingPath = (): string | null =>
  entitledSections()[0]?.items[0]?.to ?? null;
