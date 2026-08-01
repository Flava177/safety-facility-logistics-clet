import { IconName } from 'shared/components/Icon';
import { ProgrammeCode, SystemCode, entitledTo, entitledToSystem } from './programmes';
import { permits } from './actorPermissions';
import type { SflPermission } from './permissions';
import { isPersona, PersonaCode } from './personas';

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
  /**
   * The permission this screen's first read requires, when it is more than the section's system.
   *
   * **Absent means the system entitlement is the whole requirement**, which is true of most screens —
   * a role entitled to S171 can read courier items, manifests and exception cases.
   *
   * It is not true of dashboards. A mailroom officer is entitled to S171 and holds no
   * `DISPATCH_REPORT_READ`, so before this existed they were offered the dispatch dashboard as their
   * landing page and met a 403 on arrival. Every code below was read off the service that enforces it,
   * not inferred from the name.
   */
  permission?: SflPermission;
  /**
   * Show this item only to a given persona.
   *
   * **The escape hatch for the one thing a permission cannot express.** `FLEET_DRIVER` holds eight
   * permissions and every one is also held by `FLEET_MANAGER`, so gating "My driving day" on a
   * permission would offer it to the fleet office as their landing page. What makes somebody a
   * driver is what they *cannot* do, and `personas.ts` encodes that using the same
   * narrowest-role rule the services already enforce.
   *
   * Use it only for personal landings. Every operational screen stays permission-gated, because a
   * screen should be offered exactly when the service will answer it.
   */
  persona?: PersonaCode;
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

/**
 * S152 CAFM/IWMS routes.
 *
 * The first IFIMP module in this dashboard, and the platform S153 and S159 will attach to — so these
 * paths are `/facilities/...` rather than `/cafm/...`: a user is looking at facilities, and which
 * system inside IFIMP serves a screen is not their problem.
 *
 * Readiness assessments have a register and a detail, because an assessment is a signed record an
 * auditor comes back to. Blockers do not: a blocker is only meaningful beside the space it blocks,
 * so it lives on the space detail screen and on the dashboard drilldown rather than as a third list
 * to cross-reference by hand.
 */
export const facilitiesPaths = {
  dashboard: '/facilities',
  sites: '/facilities/sites',
  siteDetail: (siteId: string) => `/facilities/sites/${siteId}`,
  /*
    Buildings have a detail route and no register. A building is only ever reached from the site that
    owns it — nobody searches an estate for a building — and a fourth register would be a sidebar
    entry whose whole content is "choose a site first".
  */
  buildingDetail: (buildingId: string) => `/facilities/buildings/${buildingId}`,
  spaces: '/facilities/spaces',
  spaceDetail: (roomId: string) => `/facilities/spaces/${roomId}`,
  assets: '/facilities/assets',
  assetDetail: (assetId: string) => `/facilities/assets/${assetId}`,
  zones: '/facilities/zones',
  devices: '/facilities/devices',
  assessments: '/facilities/assessments',
  assessmentDetail: (assessmentId: string) => `/facilities/assessments/${assessmentId}`,
  checklists: '/facilities/checklists',
  checklistDetail: (checklistId: string) => `/facilities/checklists/${checklistId}`,
  audit: '/facilities/audit',
  configuration: '/facilities/configuration',

  // S153 CMMS. Same route base as S152 because it is the same service and the same programme; the
  // system code differs, which is what the route guard reads.
  faults: '/facilities/faults',
  faultDetail: (faultId: string) => `/facilities/faults/${faultId}`,
  workOrders: '/facilities/work-orders',
  workOrderDetail: (workOrderId: string) => `/facilities/work-orders/${workOrderId}`,
  schedules: '/facilities/maintenance/schedules',
  scheduleDetail: (scheduleId: string) => `/facilities/maintenance/schedules/${scheduleId}`,
  vendors: '/facilities/maintenance/vendors',
  evidenceDetail: (evidenceId: string) => `/facilities/maintenance-evidence/${evidenceId}`,
};

/**
 * S159 room and resource booking routes.
 *
 * Under `/bookings` rather than `/facilities/bookings`, which is the odd one out among the three
 * IFIMP systems and is deliberate. S152 and S153 are read by the people who run the estate; the
 * booking diary is read by everybody who ever needs a room, and most of them do not think of
 * themselves as visiting facilities at all. A path a lecturer can be told over the phone is worth
 * more than a URL that mirrors the service topology.
 *
 * Availability is a destination, not a dialog on the diary. It is where a booking begins, it takes
 * eight fields, and its answer is a page of spaces with reasons — none of which fits in a modal, and
 * all of which somebody will want to link to.
 */
export const bookingPaths = {
  diary: '/bookings',
  /*
    Static siblings of `:bookingId`. React Router ranks a static segment above a dynamic one, so
    `/bookings/availability` never resolves as a booking whose id is the word "availability" — and
    ids are UUIDs regardless. Keep new static children out of the UUID shape and this stays true.
  */
  availability: '/bookings/availability',
  resources: '/bookings/resources',
  setupTasks: '/bookings/turnaround',
  bookingDetail: (bookingId: string) => `/bookings/${bookingId}`,
};

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
  cards: '/fuel/cards',
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

/**
 * Personal landings — the "what do I have to do today" views.
 *
 * Under `/me/` rather than inside a system's routes because they cross systems: a driver's day is
 * S166 assignments and an S168 logbook, and filing either under the other would be arbitrary. The
 * URL says whose view it is, which is the honest description.
 */
export const mePaths = {
  driverDay: '/me/driving',
  myRequests: '/me/requests',
  myQueue: '/me/queue',
  mailroom: '/me/mailroom',
  centreReceipts: '/me/receipts',
  assurance: '/me/assurance',
} as const;


export const navSections: NavSection[] = [
  // ── Personal landings ───────────────────────────────────────────────────────────────────────
  //
  // First in the list on purpose. `landingPath()` returns the first item of the first entitled
  // section, so putting these ahead of the operator sections is what makes a driver open on their
  // own day rather than on the fleet dashboard — with no change to the router or the shell.
  //
  // Each is `persona`-gated, so an operator never sees them: the sections below are unchanged for
  // everybody who was already served.
  {
    heading: 'My work',
    programme: 'FTLMP',
    system: 'S166',
    items: [
      {
        label: 'My driving day',
        to: mePaths.driverDay,
        icon: 'truck',
        description: 'Today’s assignments, my logbook and pre-trip checks',
        // Enforced by FuelApplicationService.logbooks, narrowed per record on created_by.
        permission: 'FUEL_LOGBOOK_READ',
        persona: 'driver',
      },
    ],
  },
  {
    heading: 'My work',
    programme: 'FTLMP',
    system: 'S171',
    items: [
      {
        label: 'Mailroom',
        to: mePaths.mailroom,
        icon: 'inbox',
        description: 'Register inbound items and today’s distribution',
        // Enforced by DispatchAccessPolicy on the courier item register.
        permission: 'DISPATCH_ITEM_READ',
        persona: 'mailroom',
      },
      {
        label: 'Centre receipts',
        to: mePaths.centreReceipts,
        icon: 'clipboard',
        description: 'Confirm receipt, record a variance, chase returns',
        // Enforced by DispatchAccessPolicy; confirmation needs DISPATCH_RECEIPT_CONFIRM.
        permission: 'DISPATCH_MANIFEST_READ',
        persona: 'centre',
      },
    ],
  },
  {
    heading: 'My work',
    programme: 'IFIMP',
    system: 'S153',
    items: [
      {
        label: 'My requests',
        to: mePaths.myRequests,
        icon: 'clipboard',
        description: 'The bookings and faults I raised',
        // Enforced by FacilityFaultService.requesterFilter and its booking twin.
        permission: 'FACILITIES_FAULT_READ',
        persona: 'requester',
      },
      {
        label: 'My work queue',
        to: mePaths.myQueue,
        icon: 'wrench',
        description: 'The jobs assigned to me',
        // Enforced by WorkOrderApplicationService.assertVisible, per record on assignedTo.
        permission: 'FACILITIES_WORK_ORDER_READ',
        persona: 'technician',
      },
    ],
  },
  {
    heading: 'Assurance',
    programme: 'IFIMP',
    system: 'S152',
    items: [
      {
        label: 'Audit & evidence',
        to: mePaths.assurance,
        icon: 'shield-check',
        description: 'Chain verification, evidence and denials across every system',
        // Enforced by the facilities audit endpoints; FTLMP has its own, read side by side.
        permission: 'FACILITIES_AUDIT_READ',
        persona: 'assurance',
      },
    ],
  },
  {
    // S152 leads the list because IFIMP is the first programme in `allProgrammes`, and an actor
    // entitled to both lands on their facilities dashboard rather than on fleet's.
    heading: 'Facility operations',
    programme: 'IFIMP',
    system: 'S152',
    items: [
      {
        label: 'Facilities dashboard',
        to: facilitiesPaths.dashboard,
        icon: 'dashboard',
        description: 'Readiness, blockers and examination risk',
        // Enforced by FacilityDashboardService.
        permission: 'FACILITIES_DASHBOARD_READ',
      },
      {
        label: 'Readiness assessments',
        to: facilitiesPaths.assessments,
        icon: 'clipboard',
        matchPrefix: facilitiesPaths.assessments,
        description: 'Inspect a space against its checklist',
        // Enforced by ReadinessApplicationService.assessments.
        permission: 'FACILITIES_READINESS_READ',
      },
    ],
  },
  {
    // S153. Its own section rather than items inside 'Facility operations', because maintenance has
    // a different audience: a technician and a contractor live here and never open the estate
    // registers, and a section they can read end to end is easier to trust than three items
    // scattered through one they mostly cannot.
    heading: 'Maintenance',
    programme: 'IFIMP',
    system: 'S153',
    items: [
      {
        label: 'Faults',
        to: facilitiesPaths.faults,
        icon: 'flag',
        matchPrefix: facilitiesPaths.faults,
        description: 'Reported problems, triage and SLA',
        // Enforced by FacilityFaultService. A requester holds this and sees only their own.
        permission: 'FACILITIES_FAULT_READ',
      },
      {
        label: 'Work orders',
        to: facilitiesPaths.workOrders,
        icon: 'wrench',
        matchPrefix: facilitiesPaths.workOrders,
        description: 'The queue, its assignees and what is overdue',
        // Enforced by WorkOrderApplicationService, which also narrows a vendor to their own.
        permission: 'FACILITIES_WORK_ORDER_READ',
      },
      {
        label: 'Preventive schedules',
        to: facilitiesPaths.schedules,
        icon: 'calendar',
        matchPrefix: facilitiesPaths.schedules,
        description: 'Planned servicing, and what it has raised',
        permission: 'FACILITIES_PM_SCHEDULE_READ',
      },
      {
        label: 'Vendors',
        to: facilitiesPaths.vendors,
        icon: 'users',
        description: 'Contractors, contracts and response times',
        permission: 'FACILITIES_VENDOR_READ',
      },
    ],
  },
  {
    // S159. Its own section rather than items inside 'Facility operations', for the same reason
    // maintenance has one: the audience is different. A lecturer or a registry clerk books a room and
    // opens nothing else in this programme, and a section they can read end to end is easier to trust
    // than three items scattered through one they mostly cannot.
    heading: 'Room booking',
    programme: 'IFIMP',
    system: 'S159',
    items: [
      {
        label: 'Booking diary',
        to: bookingPaths.diary,
        icon: 'calendar',
        // Not `matchPrefix`: the diary is the index of `/bookings`, and a prefix match would keep it
        // highlighted while the operator is on turnaround or the resource register.
        description: 'What is booked, and what the estate thinks of it',
        // Enforced by BookingApplicationService.search. A requester holds this and sees only their own.
        permission: 'FACILITIES_BOOKING_READ',
      },
      {
        label: 'Find a space',
        to: bookingPaths.availability,
        icon: 'search',
        description: 'What can take a window, and what cannot',
        // The availability endpoints are read with BOOKING_READ; the request that follows needs more,
        // and the page hides the control rather than the screen.
        permission: 'FACILITIES_BOOKING_READ',
      },
      {
        label: 'Room turnaround',
        to: bookingPaths.setupTasks,
        icon: 'clipboard',
        description: 'What has to happen to a room before its next booking',
        // BOOKING_READ, not SETUP_TASK_MANAGE — read off `BookingSetupService.queue`, which gates the
        // queue on reading bookings and reserves SETUP_TASK_MANAGE for raising and resolving a task.
        // Gating the screen on the write permission would hide the queue from everybody who can only
        // look at it, which is most of the people who need to.
        permission: 'FACILITIES_BOOKING_READ',
      },
      {
        label: 'Bookable resources',
        to: bookingPaths.resources,
        icon: 'package',
        description: 'Projectors, furniture and what else can be booked',
        permission: 'FACILITIES_RESOURCE_READ',
      },
    ],
  },
  {
    heading: 'Estate registers',
    programme: 'IFIMP',
    system: 'S152',
    items: [
      {
        label: 'Sites',
        to: facilitiesPaths.sites,
        icon: 'map-pin',
        matchPrefix: facilitiesPaths.sites,
        description: 'Centres, and the operating mode each is in',
        permission: 'FACILITIES_SITE_READ',
      },
      {
        label: 'Spaces',
        to: facilitiesPaths.spaces,
        icon: 'building',
        matchPrefix: facilitiesPaths.spaces,
        description: 'Rooms, halls and courtrooms with their readiness',
        permission: 'FACILITIES_SPACE_READ',
      },
      {
        label: 'Facility assets',
        to: facilitiesPaths.assets,
        icon: 'wrench',
        matchPrefix: facilitiesPaths.assets,
        description: 'Fixed plant, its condition and what it serves',
        permission: 'FACILITIES_ASSET_READ',
      },
      {
        label: 'Zones',
        to: facilitiesPaths.zones,
        icon: 'layers',
        description: 'What each zone covers, for safety and emergency',
        permission: 'FACILITIES_ZONE_READ',
      },
      {
        label: 'Device references',
        to: facilitiesPaths.devices,
        icon: 'activity',
        description: 'Cameras, readers and panels, and where they sit',
        permission: 'FACILITIES_DEVICE_REFERENCE_READ',
      },
    ],
  },
  {
    heading: 'Facility assurance',
    programme: 'IFIMP',
    system: 'S152',
    items: [
      {
        label: 'Readiness checklists',
        to: facilitiesPaths.checklists,
        icon: 'clipboard-list',
        matchPrefix: facilitiesPaths.checklists,
        description: 'The questions an assessment asks, and what a failure costs',
        permission: 'FACILITIES_READINESS_READ',
      },
      {
        label: 'Audit & integrity',
        to: facilitiesPaths.audit,
        icon: 'shield-lock',
        description: 'Every state change, and the chain replay that proves it',
        // Enforced by FacilitiesGovernanceService.search.
        permission: 'FACILITIES_AUDIT_READ',
      },
      {
        label: 'Configuration',
        to: facilitiesPaths.configuration,
        icon: 'gauge',
        description: 'Thresholds the rules are read from, and their versions',
        permission: 'FACILITIES_CONFIG_READ',
      },
    ],
  },
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
        // Enforced by FleetDashboardApplicationService.
        permission: 'FLEET_DASHBOARD_READ',
      },
      {
        label: 'Trips & assignments',
        to: fleetPaths.trips,
        icon: 'route',
        matchPrefix: fleetPaths.trips,
        description: 'Plan, assign, start and close movements',
        // Enforced by TripApplicationService. A driver holds this and sees the register; planning,
        // assigning and closing are separate permissions the page gates its controls on.
        permission: 'FLEET_TRIP_READ',
      },
      {
        label: 'Workflow queue',
        to: fleetPaths.workflow,
        icon: 'workflow',
        matchPrefix: fleetPaths.workflow,
        description: 'Inspections, defects and escalations',
        // A driver does NOT hold this. The queue is the supervisor's view of inspections, defects
        // and escalations across the fleet; a driver records an inspection against their own trip
        // and has no business reading everybody else's defects.
        permission: 'FLEET_WORKFLOW_READ',
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
        // A driver holds this: they need to look up the vehicle they are taking out. Registering,
        // editing and retiring one are FLEET_VEHICLE_MANAGE, which they do not hold — the page
        // hides those controls rather than offering a button the service refuses.
        permission: 'FLEET_VEHICLE_READ',
      },
      {
        label: 'Driver register',
        to: fleetPaths.drivers,
        icon: 'driver',
        matchPrefix: fleetPaths.drivers,
        description: 'Licence standing and eligibility',
        /*
          A driver holds FLEET_DRIVER_READ and sees the list, the same way they see the vehicle
          register: they work alongside these people and need to look them up. What they do not hold
          is FLEET_DRIVER_MANAGE — so no registering, editing or retiring, including of themselves —
          and no FLEET_DRIVER_SENSITIVE_READ, so licence numbers arrive masked from the service
          rather than being hidden by this screen.

          Medical clearance dates and eligibility standing are still visible to them, which is a
          deliberate accepted trade rather than an oversight: the register is a working document for
          people who cover each other's trips, and eligibility is why a colleague cannot.
        */
        permission: 'FLEET_DRIVER_READ',
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
        // Enforced by the compliance and service-record endpoints. A driver holds neither.
        permission: 'FLEET_COMPLIANCE_MANAGE',
      },
      {
        label: 'Evidence & audit',
        to: fleetPaths.governance,
        icon: 'document',
        description: 'Closure evidence and audit trail',
        // FLEET_EVIDENCE_READ, not FLEET_EVIDENCE_REGISTER. A driver holds the second — they attach
        // evidence to their own trip closure — and that is deliberately not a licence to read the
        // fleet's evidence library or replay the audit chain.
        permission: 'FLEET_EVIDENCE_READ',
      },
      {
        label: 'Integration health',
        to: fleetPaths.integrations,
        icon: 'cloud',
        description: 'Inbound and outbound message flow',
        // Enforced by FleetIntegrationApplicationService.
        permission: 'FLEET_INTEGRATION_HEALTH_READ',
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
        // Enforced by FuelApplicationService.dashboard.
        permission: 'FUEL_REPORT_READ',
      },
      {
        label: 'Fuel transactions',
        to: fuelPaths.transactions,
        icon: 'coins',
        matchPrefix: fuelPaths.transactions,
        description: 'Captured, imported and provider transactions',
        // Driver-only actors are narrowed by FuelApplicationService.transactions, so this register
        // can be safely shown to drivers without leaking colleagues' fills.
        permission: 'FUEL_TRANSACTION_READ',
      },
      {
        label: 'Driver logbooks',
        to: fuelPaths.logbooks,
        icon: 'book',
        matchPrefix: fuelPaths.logbooks,
        description: 'Journey records through review to approval',
        // Narrowed per record in SQL by FuelApplicationService.logbooks on created_by, so a driver
        // holding this genuinely sees only their own.
        permission: 'FUEL_LOGBOOK_READ',
      },
      {
        label: 'Reconciliation',
        to: fuelPaths.reconciliation,
        icon: 'scale',
        description: 'Run the policy rules and read the outcome',
        permission: 'FUEL_RECONCILIATION_RUN',
      },
      {
        label: 'Anomaly cases',
        to: fuelPaths.anomalies,
        icon: 'alert-triangle',
        matchPrefix: fuelPaths.anomalies,
        description: 'Exception queue, explanation and closure',
        permission: 'FUEL_ANOMALY_READ',
      },
      {
        label: 'Fuel cards',
        to: fuelPaths.cards,
        icon: 'shield-lock',
        matchPrefix: fuelPaths.cards,
        description: 'Masked card register, assignments and card limits',
        permission: 'FUEL_CARD_READ',
      },
      {
        label: 'CSV imports',
        to: fuelPaths.imports,
        icon: 'upload',
        description: 'Bulk capture with row-level outcomes',
        permission: 'FUEL_TRANSACTION_IMPORT',
      },
      {
        label: 'Fuel policies',
        to: fuelPaths.policies,
        icon: 'shield-check',
        matchPrefix: fuelPaths.policies,
        description: 'Effective-dated limits the rules are read from',
        // The limits every reconciliation is judged against. A driver being judged by them is not a
        // reason to let them read — still less edit — the thresholds.
        permission: 'FUEL_POLICY_READ',
      },
      {
        label: 'Provider integration',
        to: fuelPaths.integrations,
        icon: 'cloud',
        description: 'Provider ingest and outbound publication',
        permission: 'FUEL_INTEGRATION_REPLAY',
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
        // Enforced by DispatchDashboardService.
        permission: 'DISPATCH_REPORT_READ',
      },
      {
        label: 'Courier items',
        to: dispatchPaths.items,
        icon: 'package',
        matchPrefix: dispatchPaths.items,
        description: 'Every tracked item, inbound and outbound',
        permission: 'DISPATCH_ITEM_READ',
      },
      {
        label: 'Manifests',
        to: dispatchPaths.manifests,
        icon: 'clipboard-list',
        matchPrefix: dispatchPaths.manifests,
        description: 'Seals, custody, receipt and the return leg',
        permission: 'DISPATCH_MANIFEST_READ',
      },
      {
        label: 'Inbound mail',
        to: dispatchPaths.inbound,
        icon: 'inbox',
        description: 'Registration and acknowledged distribution',
        permission: 'DISPATCH_ITEM_READ',
      },
      {
        label: 'Exception cases',
        to: dispatchPaths.exceptions,
        icon: 'alert-triangle',
        matchPrefix: dispatchPaths.exceptions,
        description: 'Custody gaps, variances and discrepancies',
        permission: 'DISPATCH_EXCEPTION_READ',
      },
      {
        label: 'Scan imports',
        to: dispatchPaths.scans,
        icon: 'upload',
        description: 'Scanner batches and per-row outcomes',
        permission: 'DISPATCH_MANIFEST_READ',
      },
      {
        label: 'Scanner integration',
        to: dispatchPaths.integrations,
        icon: 'cloud',
        description: 'Scanner and carrier feeds, outbound publication',
        permission: 'DISPATCH_INTEGRATION_REPLAY',
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
        // Enforced by EmergencyDashboardService.
        permission: 'EMERGENCY_REPORT_READ',
      },
      {
        label: 'Activations',
        to: emergencyPaths.activations,
        icon: 'megaphone',
        matchPrefix: emergencyPaths.activations,
        description: 'Compose, approve, send, stand down and close',
        permission: 'EMERGENCY_ACTIVATION_READ',
      },
      {
        label: 'Break glass',
        to: emergencyPaths.breakGlass,
        icon: 'zap',
        description: 'Declared-emergency send with no approval',
        // The one screen in the platform that sends without approval. It is offered only to an
        // actor who may actually press it — a break-glass page somebody cannot use is worse than
        // absent, because in a declared emergency they will try.
        permission: 'EMERGENCY_BREAK_GLASS_SEND',
      },
      {
        label: 'Templates & scenarios',
        to: emergencyPaths.templates,
        icon: 'document',
        matchPrefix: emergencyPaths.templates,
        description: 'What a broadcast says, and what cites it',
        permission: 'EMERGENCY_TEMPLATE_READ',
      },
      {
        label: 'Audiences & zones',
        to: emergencyPaths.audiences,
        icon: 'users',
        description: 'Who a broadcast reaches, and where',
        permission: 'EMERGENCY_AUDIENCE_READ',
      },
      {
        label: 'Drills',
        to: emergencyPaths.drills,
        icon: 'target',
        description: 'Rehearsals and notification performance',
        permission: 'EMERGENCY_REPORT_READ',
      },
      {
        label: 'Provider integration',
        to: emergencyPaths.integrations,
        icon: 'cloud',
        description: 'Outbound publication and the callback path',
        permission: 'EMERGENCY_INTEGRATION_REPLAY',
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
/** No persona named means "everyone who is entitled" — the ordinary case. */
const suitsPersona = (persona?: PersonaCode): boolean => persona === undefined || isPersona(persona);

export const entitledSections = (): NavSection[] =>
  navSections
    .filter((section) => entitledTo(section.programme) && entitledToSystem(section.system))
    // Then drop the items the actor cannot read, and any section left with none — an empty heading is
    // worse than no heading. `permits` returns true for everything when the services could not be
    // asked, so a failed lookup never hides a screen.
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => permits(item.permission) && suitsPersona(item.persona)),
    }))
    .filter((section) => section.items.length > 0);

/**
 * Where an actor lands when they open the application.
 *
 * The first destination of the first programme they are entitled to — **not** the fleet dashboard,
 * which is only the right answer for a fleet user. `null` when they are entitled to nothing, which
 * the router turns into an explanation rather than a redirect loop.
 */
export const landingPath = (): string | null =>
  entitledSections()[0]?.items[0]?.to ?? null;
