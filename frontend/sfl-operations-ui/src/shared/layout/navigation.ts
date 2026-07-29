import { IconName } from 'shared/components/Icon';

/**
 * Console navigation.
 *
 * Only destinations that are built and wired to the Fleet service appear here. Modules that do not
 * exist yet are not listed at all — a greyed-out "coming soon" entry costs an operator a click to
 * discover nothing, and it makes a working console look half-finished.
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

export const navSections: NavSection[] = [
  {
    heading: 'Operations',
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
];

export const directorate = {
  name: 'Safety, Facilities & Logistics',
  shortName: 'SFL Operations',
  module: 'Fleet & Logistics',
  parentOrganisation: 'CLET',
};
