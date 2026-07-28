/**
 * SFL module navigation.
 *
 * The directorate runs five systems; Fleet & Logistics (S166) is the first with a UI. The others
 * are listed so the shell shows the real shape of the platform, and are marked `available: false`
 * until their module lands. Nothing here links to a screen that does not exist.
 */

export interface NavItem {
  label: string;
  to: string;
  icon: string;
  available?: boolean;
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

export const navSections: NavSection[] = [
  {
    heading: 'Fleet & Logistics',
    items: [
      {
        label: 'Operations dashboard',
        to: fleetPaths.dashboard,
        icon: 'material-symbols:dashboard-customize-outline-rounded',
        available: true,
      },
      {
        label: 'Vehicle register',
        to: fleetPaths.vehicles,
        icon: 'material-symbols:local-shipping-outline-rounded',
        available: true,
      },
      {
        label: 'Driver register',
        to: fleetPaths.drivers,
        icon: 'material-symbols:badge-outline',
        available: true,
      },
      {
        label: 'Trips & assignments',
        to: fleetPaths.trips,
        icon: 'material-symbols:conversion-path',
        available: true,
      },
      {
        label: 'Workflow queue',
        to: fleetPaths.workflow,
        icon: 'material-symbols:pending-actions-rounded',
        available: true,
      },
      {
        label: 'Compliance & service',
        to: fleetPaths.compliance,
        icon: 'material-symbols:verified-user-outline-rounded',
        available: true,
      },
      {
        label: 'Evidence & audit',
        to: fleetPaths.governance,
        icon: 'material-symbols:lab-profile-outline-rounded',
        available: true,
      },
      {
        label: 'Integration health',
        to: fleetPaths.integrations,
        icon: 'material-symbols:cloud',
        available: true,
      },
    ],
  },
  {
    heading: 'Other SFL systems',
    items: [
      {
        label: 'Facilities',
        to: '/facilities',
        icon: 'material-symbols:home-pin-outline',
        available: false,
      },
      {
        label: 'Safety & security',
        to: '/safety',
        icon: 'material-symbols:shield-outline',
        available: false,
      },
      {
        label: 'Asset visibility',
        to: '/assets',
        icon: 'material-symbols:inventory-2-outline-rounded',
        available: false,
      },
      {
        label: 'Emergency notification',
        to: '/emergency',
        icon: 'material-symbols:notifications-outline-rounded',
        available: false,
      },
    ],
  },
];

export const directorate = {
  name: 'Safety, Facilities & Logistics',
  shortName: 'SFL Operations',
  parentOrganisation: 'CLET',
  url: 'https://zesty-beignet-81d70f.netlify.app/',
};
