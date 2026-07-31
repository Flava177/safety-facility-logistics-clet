/**
 * The S152 enumerations, transcribed from the facilities service.
 *
 * Each is declared as a `const` array plus a derived union, so the same list can be rendered in a
 * dropdown and type-check an API call. A value the service does not know is a request it will refuse,
 * so these must stay in step with the Java enums they mirror.
 */

/** What a space is for. Drives the bookable and examination-capable defaults on registration. */
export const spaceTypes = [
  'OFFICE',
  'MEETING_ROOM',
  'LECTURE_HALL',
  'MOOT_COURTROOM',
  'EXAMINATION_HALL',
  'LABORATORY',
  'LIBRARY',
  'AUDITORIUM',
  'STORE',
  'PLANT_ROOM',
  'CIRCULATION',
  'SANITARY',
  'RECEPTION',
  'CAFETERIA',
  'ACCOMMODATION',
  'OTHER',
] as const;
export type SpaceType = (typeof spaceTypes)[number];

/**
 * A space's readiness.
 *
 * Derived by the service from its open blockers, never set freely: `READY` is refused while a
 * critical blocker is open. Ordered worst-first, which is how the registers sort.
 */
export const readinessStatuses = ['BLOCKED', 'DEGRADED', 'UNKNOWN', 'READY'] as const;
export type LocationReadinessStatus = (typeof readinessStatuses)[number];

/**
 * How much an open blocker matters.
 *
 * `CRITICAL` is the one that forbids READY; `MAJOR` and `MINOR` degrade; `ADVISORY` is noted and
 * changes nothing. Declared worst-first.
 */
export const blockerSeverities = ['CRITICAL', 'MAJOR', 'MINOR', 'ADVISORY'] as const;
export type BlockerSeverity = (typeof blockerSeverities)[number];

/** Where a blocker came from. The resolution path differs by source. */
export const blockerSources = ['CHECKLIST_ITEM', 'ASSET', 'WORK_ORDER', 'MANUAL'] as const;
export type BlockerSource = (typeof blockerSources)[number];

/** The kind of fixed plant an asset is. */
export const assetCategories = [
  'HVAC',
  'ELECTRICAL',
  'POWER_DISTRIBUTION',
  'GENERATOR',
  'UPS',
  'PLUMBING',
  'WATER_SYSTEM',
  'LIFT',
  'FIRE_SYSTEM',
  'SECURITY_SYSTEM',
  'BUILDING_FABRIC',
  'AUDIO_VISUAL',
  'IT_INFRASTRUCTURE',
  'FURNITURE',
  'GROUNDS',
  'OTHER',
] as const;
export type AssetCategory = (typeof assetCategories)[number];

/** How much a failure of an asset matters. Sets the ceiling on the blocker severity it raises. */
export const assetCriticalities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const;
export type AssetCriticality = (typeof assetCriticalities)[number];

/** Whether an asset is working. Distinct from whether its record is in use. */
export const assetOperationalStatuses = [
  'OPERATIONAL',
  'DEGRADED',
  'UNDER_MAINTENANCE',
  'OUT_OF_SERVICE',
  'DECOMMISSIONED',
] as const;
export type AssetOperationalStatus = (typeof assetOperationalStatuses)[number];

/** Whether a record is in use. `ARCHIVED` is terminal — the service refuses any move out of it. */
export const recordLifecycleStatuses = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;
export type RecordLifecycleStatus = (typeof recordLifecycleStatuses)[number];

/** A site's operating mode. Declaring EXAMINATION applies stricter readiness across the centre. */
export const operatingModes = ['ROUTINE', 'EXAMINATION'] as const;
export type OperatingMode = (typeof operatingModes)[number];

/** What a vendor system operates and S152 only references. */
export const deviceReferenceTypes = [
  'CCTV_CAMERA',
  'ACCESS_READER',
  'BIOMETRIC_READER',
  'FIRE_PANEL',
  'INTRUSION_PANEL',
  'IOT_SENSOR',
  'NETWORK_DEVICE',
  'POWER_DEVICE',
  'ROOM_RESOURCE',
  'VEHICLE_TRACKER',
  'RFID_READER',
  'SIGNATURE_PAD',
  'OTHER',
] as const;
export type DeviceReferenceType = (typeof deviceReferenceTypes)[number];

/** What a vendor feed last reported about a device. */
export const deviceOperationalStatuses = [
  'UNKNOWN',
  'ONLINE',
  'OFFLINE',
  'DEGRADED',
  'MAINTENANCE',
] as const;
export type DeviceOperationalStatus = (typeof deviceOperationalStatuses)[number];

/** What kind of estate record a zone contains. */
export const zoneMemberTypes = ['BUILDING', 'FLOOR', 'ROOM', 'DEVICE'] as const;
export type ZoneMemberType = (typeof zoneMemberTypes)[number];

/** How a change reached the service. Recorded on every audit entry. */
export const sourceChannels = ['WEB', 'MOBILE', 'INTEGRATION', 'SCHEDULER', 'SYSTEM'] as const;
export type SourceChannel = (typeof sourceChannels)[number];

/**
 * The auditable actions.
 *
 * Used to populate the audit filter. `AUTHORIZATION_DENIED` is the one a compliance review reaches
 * for first, which is why it is offered rather than buried.
 */
export const auditActions = [
  'AUTHORIZATION_DENIED',
  'SITE_CREATED',
  'SITE_UPDATED',
  'SITE_LIFECYCLE_CHANGED',
  'SITE_OPERATING_MODE_CHANGED',
  'BUILDING_CREATED',
  'BUILDING_UPDATED',
  'FLOOR_CREATED',
  'FLOOR_UPDATED',
  'ROOM_CREATED',
  'ROOM_UPDATED',
  'ROOM_LIFECYCLE_CHANGED',
  'ROOM_READINESS_CHANGED',
  'ZONE_CREATED',
  'ZONE_UPDATED',
  'ZONE_MEMBER_ADDED',
  'ZONE_MEMBER_REMOVED',
  'DEVICE_REFERENCE_REGISTERED',
  'DEVICE_REFERENCE_UPDATED',
  'FACILITY_ASSET_REGISTERED',
  'FACILITY_ASSET_UPDATED',
  'FACILITY_ASSET_STATUS_CHANGED',
  'FACILITY_ASSET_RELOCATED',
  'READINESS_CHECKLIST_CREATED',
  'READINESS_CHECKLIST_UPDATED',
  'READINESS_ASSESSMENT_SUBMITTED',
  'READINESS_BLOCKER_RAISED',
  'READINESS_BLOCKER_RESOLVED',
  'READINESS_LOCK_ENGAGED',
  'READINESS_LOCK_RELEASED',
  'RUNTIME_CONFIGURATION_CHANGED',
  'DASHBOARD_SNAPSHOT_GENERATED',
  'AUDIT_INTEGRITY_VERIFIED',
] as const;
export type AuditAction = (typeof auditActions)[number];

/**
 * The S152 permissions the screens gate on.
 *
 * Read from `GET /actor/permissions`, which is the service's own answer rather than a guess derived
 * from role names. A control the actor cannot use is not rendered — an operator meeting a 403 on a
 * button they were offered has been misled by the screen.
 */
export type FacilitiesPermission =
  | 'FACILITIES_SITE_READ'
  | 'FACILITIES_SITE_MANAGE'
  | 'FACILITIES_SPACE_READ'
  | 'FACILITIES_SPACE_MANAGE'
  | 'FACILITIES_ZONE_READ'
  | 'FACILITIES_ZONE_MANAGE'
  | 'FACILITIES_DEVICE_REFERENCE_READ'
  | 'FACILITIES_DEVICE_REFERENCE_REGISTER'
  | 'FACILITIES_ASSET_READ'
  | 'FACILITIES_ASSET_MANAGE'
  | 'FACILITIES_READINESS_READ'
  | 'FACILITIES_READINESS_ASSESS'
  | 'FACILITIES_READINESS_OVERRIDE'
  | 'FACILITIES_READINESS_CHECKLIST_MANAGE'
  | 'FACILITIES_OPERATING_MODE_CHANGE'
  | 'FACILITIES_DASHBOARD_READ'
  | 'FACILITIES_DASHBOARD_DRILLDOWN'
  | 'FACILITIES_AUDIT_READ'
  | 'FACILITIES_AUDIT_INTEGRITY_CHECK'
  | 'FACILITIES_CONFIG_READ'
  | 'FACILITIES_CONFIG_MANAGE';

// =================================================================================================
// S153 CMMS
// =================================================================================================

/**
 * How urgent a fault is, and therefore what SLA it earns.
 *
 * The order is meaningful and relied on by two configurable thresholds — the priority at which a
 * fault blocks its space, and the priority at which closure evidence becomes mandatory. Both are
 * expressed as "at least this", so reordering this array changes behaviour.
 */
export const faultPriorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type FaultPriority = (typeof faultPriorities)[number];

/** The life of a reported fault. `REJECTED`, `DUPLICATE` and `CANCELLED` are terminal dismissals. */
export const faultStatuses = [
  'REPORTED',
  'TRIAGED',
  'WORK_ORDER_CREATED',
  'RESOLVED',
  'REJECTED',
  'DUPLICATE',
  'CANCELLED',
] as const;
export type FacilityFaultStatus = (typeof faultStatuses)[number];

/** The three outcomes `PATCH /faults/{id}/dismissal` accepts. All require a reason. */
export const faultDismissalOutcomes = ['REJECTED', 'DUPLICATE', 'CANCELLED'] as const;
export type FaultDismissalOutcome = (typeof faultDismissalOutcomes)[number];

/**
 * The life of a work order.
 *
 * `COMPLETED` and `CLOSED` are separate on purpose: the assignee says the work is done, an
 * authorised officer says it is accepted. Passing through `COMPLETED` is not mandatory — closure is
 * reachable from any working state, because the gate is the closing permission and the evidence
 * rule rather than the route taken.
 */
export const workOrderStatuses = [
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'ON_HOLD',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
] as const;
export type WorkOrderStatus = (typeof workOrderStatuses)[number];

/** Why a work order exists. Closing a `PREVENTIVE` one records the service against its asset. */
export const workOrderTypes = ['CORRECTIVE', 'PREVENTIVE', 'INSPECTION'] as const;
export type WorkOrderType = (typeof workOrderTypes)[number];

/** What a piece of evidence is. `INVOICE` does not satisfy the closure requirement. */
export const evidenceTypes = [
  'BEFORE_PHOTO',
  'AFTER_PHOTO',
  'SERVICE_REPORT',
  'CERTIFICATE',
  'INVOICE',
  'OTHER',
] as const;
export type EvidenceType = (typeof evidenceTypes)[number];

/** How long evidence must be kept. Mandatory on every attachment — SRS-SFL-S153-03. */
export const retentionClasses = [
  'OPERATIONAL',
  'COMPLIANCE',
  'SAFETY_CRITICAL',
  'EXAMINATION',
  'LEGAL',
] as const;
export type RetentionClass = (typeof retentionClasses)[number];
