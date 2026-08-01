/**
 * Enumerations mirrored from the Fleet & Logistics service domain model.
 *
 * Values are the wire values — the Java enum constant names. Labels are UI-only; the service never
 * sees them. Every list here was read off the corresponding
 * `gh.edu.clet.sfl.fleetlogistics.fleet.domain.model` enum, not inferred from documentation.
 */

export const VEHICLE_CATEGORIES = [
  'SALOON_CAR',
  'PICKUP',
  'FOUR_WHEEL_DRIVE',
  'MINIBUS',
  'BUS',
  'TRUCK',
  'MOTORCYCLE',
  'AMBULANCE',
  'UTILITY',
] as const;
export type VehicleCategory = (typeof VEHICLE_CATEGORIES)[number];

export const VEHICLE_LIFECYCLE_STATUSES = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;
export type VehicleLifecycleStatus = (typeof VEHICLE_LIFECYCLE_STATUSES)[number];

export const VEHICLE_SERVICE_STATUSES = ['IN_SERVICE', 'DUE', 'OVERDUE', 'OUT_OF_SERVICE'] as const;
export type VehicleServiceStatus = (typeof VEHICLE_SERVICE_STATUSES)[number];

export const VEHICLE_AVAILABILITY_STATUSES = [
  'AVAILABLE',
  'RESERVED',
  'ASSIGNED',
  'IN_USE',
  'UNAVAILABLE',
] as const;
export type VehicleAvailabilityStatus = (typeof VEHICLE_AVAILABILITY_STATUSES)[number];

export const READINESS_STATUSES = ['READY', 'CONDITIONALLY_READY', 'NOT_READY'] as const;
export type ReadinessStatus = (typeof READINESS_STATUSES)[number];

export const BLOCKER_SEVERITIES = ['WARNING', 'BLOCKING'] as const;
export type BlockerSeverity = (typeof BLOCKER_SEVERITIES)[number];

export const OPERATING_MODES = ['ROUTINE', 'EXAMINATION', 'EMERGENCY', 'MAINTENANCE'] as const;
export type OperatingMode = (typeof OPERATING_MODES)[number];

export const DRIVER_LIFECYCLE_STATUSES = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'] as const;
export type DriverLifecycleStatus = (typeof DRIVER_LIFECYCLE_STATUSES)[number];

export const DRIVER_ELIGIBILITY_STATUSES = [
  'ELIGIBLE',
  'CONDITIONAL',
  'INELIGIBLE',
  'SUSPENDED',
] as const;
export type DriverEligibilityStatus = (typeof DRIVER_ELIGIBILITY_STATUSES)[number];

export const LICENCE_CLASSES = ['A', 'B', 'C', 'D', 'E', 'L'] as const;
export type LicenceClass = (typeof LICENCE_CLASSES)[number];

export const TRIP_STATUSES = [
  'PLANNED',
  'ASSIGNED',
  'IN_PROGRESS',
  'ON_HOLD',
  'COMPLETED',
  'CANCELLED',
] as const;
export type TripStatus = (typeof TRIP_STATUSES)[number];

export const INSPECTION_TYPES = ['PRE_TRIP', 'POST_TRIP', 'PERIODIC', 'DEFECT_FOLLOW_UP'] as const;
export type InspectionType = (typeof INSPECTION_TYPES)[number];

export const INSPECTION_STATUSES = ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED'] as const;
export type InspectionStatus = (typeof INSPECTION_STATUSES)[number];

export const INSPECTION_RESULTS = ['PASSED', 'PASSED_WITH_DEFECTS', 'FAILED'] as const;
export type InspectionResult = (typeof INSPECTION_RESULTS)[number];

export const DEFECT_SEVERITIES = ['ADVISORY', 'MINOR', 'MAJOR', 'CRITICAL'] as const;
export type DefectSeverity = (typeof DEFECT_SEVERITIES)[number];

export const COMPLIANCE_DOCUMENT_TYPES = [
  'ROADWORTHINESS_CERTIFICATE',
  'INSURANCE_CERTIFICATE',
  'VEHICLE_REGISTRATION',
  'DVLA_INSPECTION_REPORT',
  'COMMERCIAL_PERMIT',
  'EMISSIONS_CERTIFICATE',
  'FIRE_EXTINGUISHER_CERTIFICATE',
] as const;
export type ComplianceDocumentType = (typeof COMPLIANCE_DOCUMENT_TYPES)[number];

/** Mandatory types raise `COMPLIANCE_DOCUMENT_MISSING` when absent (service-side `isMandatory()`). */
export const MANDATORY_COMPLIANCE_DOCUMENT_TYPES: ComplianceDocumentType[] = [
  'ROADWORTHINESS_CERTIFICATE',
  'INSURANCE_CERTIFICATE',
  'VEHICLE_REGISTRATION',
];

export const COMPLIANCE_DOCUMENT_STATUSES = [
  'ACTIVE',
  'EXPIRING',
  'EXPIRED',
  'SUPERSEDED',
  'REVOKED',
] as const;
export type ComplianceDocumentStatus = (typeof COMPLIANCE_DOCUMENT_STATUSES)[number];

export const EVIDENCE_RETENTION_CLASSES = [
  'OPERATIONAL_1_YEAR',
  'COMPLIANCE_7_YEARS',
  'INCIDENT_10_YEARS',
  'LEGAL_HOLD',
] as const;
export type EvidenceRetentionClass = (typeof EVIDENCE_RETENTION_CLASSES)[number];

export const EVIDENCE_EXPORT_STATUSES = ['REQUESTED', 'APPROVED', 'REJECTED', 'EXPORTED'] as const;
export type EvidenceExportStatus = (typeof EVIDENCE_EXPORT_STATUSES)[number];

export const SERVICE_TYPES = [
  'ROUTINE_SERVICE',
  'MAJOR_SERVICE',
  'REPAIR',
  'TYRE_REPLACEMENT',
  'BODYWORK',
  'DEFECT_RECTIFICATION',
  'STATUTORY_INSPECTION',
] as const;
export type ServiceType = (typeof SERVICE_TYPES)[number];

export const SERVICE_OUTCOMES = [
  'COMPLETED',
  'COMPLETED_WITH_ADVISORIES',
  'INCOMPLETE',
  'FAILED',
] as const;
export type ServiceOutcome = (typeof SERVICE_OUTCOMES)[number];

export const FLEET_WORKFLOW_TYPES = [
  'VEHICLE_DEFECT',
  'COMPLIANCE_RENEWAL',
  'SERVICE_SCHEDULING',
  'DRIVER_ELIGIBILITY',
  'TRIP_EXCEPTION',
  'INTEGRATION_FAILURE',
  'EVIDENCE_REVIEW',
  'ODOMETER_CORRECTION',
] as const;
export type FleetWorkflowType = (typeof FLEET_WORKFLOW_TYPES)[number];

export const FLEET_WORKFLOW_STATUSES = [
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'ON_HOLD',
  'ESCALATED',
  'CLOSED',
  'CANCELLED',
  'REOPENED',
] as const;
export type FleetWorkflowStatus = (typeof FLEET_WORKFLOW_STATUSES)[number];

export const WORKFLOW_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const;
export type WorkflowPriority = (typeof WORKFLOW_PRIORITIES)[number];

export const WORKFLOW_SEVERITIES = ['MINOR', 'MODERATE', 'MAJOR', 'CRITICAL'] as const;
export type WorkflowSeverity = (typeof WORKFLOW_SEVERITIES)[number];

export const WORKFLOW_ACTIONS = [
  'CREATED',
  'ASSIGNED',
  'REASSIGNED',
  'STARTED',
  'HELD',
  'RESUMED',
  'ESCALATED',
  'CANCELLED',
  'CLOSED',
  'REOPENED',
  'COMMENTED',
] as const;
export type WorkflowAction = (typeof WORKFLOW_ACTIONS)[number];

export const INTEGRATION_MESSAGE_STATUSES = [
  'ACCEPTED',
  'PROCESSED',
  'REJECTED',
  'DEAD_LETTER',
] as const;
export type IntegrationMessageStatus = (typeof INTEGRATION_MESSAGE_STATUSES)[number];

export const READINESS_BLOCKER_CODES = [
  'VEHICLE_NOT_ACTIVE',
  'VEHICLE_SUSPENDED',
  'VEHICLE_ARCHIVED',
  'VEHICLE_OUT_OF_SERVICE',
  'SERVICE_OVERDUE',
  'SERVICE_DUE_SOON',
  'COMPLIANCE_DOCUMENT_MISSING',
  'COMPLIANCE_DOCUMENT_EXPIRED',
  'COMPLIANCE_DOCUMENT_EXPIRING',
  'MANDATORY_INSPECTION_MISSING',
  'INSPECTION_FAILED',
  'OPEN_CRITICAL_DEFECT',
  'VEHICLE_ASSIGNMENT_CONFLICT',
  'DRIVER_ASSIGNMENT_CONFLICT',
  'DRIVER_MISSING',
  'DRIVER_INELIGIBLE',
  'DRIVER_NOT_ACTIVE',
  'DRIVER_SUSPENDED',
  'DRIVER_LICENCE_EXPIRED',
  'DRIVER_LICENCE_EXPIRING',
  'DRIVER_LICENCE_CLASS_MISMATCH',
  'DRIVER_MEDICAL_CLEARANCE_EXPIRED',
  'DRIVER_MEDICAL_CLEARANCE_EXPIRING',
  'SITE_RESTRICTION',
  'DRIVER_SITE_RESTRICTION',
  'OPERATING_MODE_RESTRICTION',
  'EMERGENCY_ONLY_RESTRICTION',
  'MISSING_REQUIRED_EVIDENCE',
  'ODOMETER_PROVENANCE_STALE',
] as const;
export type ReadinessBlockerCode = (typeof READINESS_BLOCKER_CODES)[number];

/** Turns `PASSED_WITH_DEFECTS` into `Passed with defects` for display. */
export const humanise = (value: string | null | undefined): string => {
  if (!value) {
    return '—';
  }
  const spaced = value.replace(/_/g, ' ').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
};
