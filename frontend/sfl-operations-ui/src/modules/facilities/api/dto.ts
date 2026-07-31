import type {
  AssetCategory,
  AssetCriticality,
  AssetOperationalStatus,
  AuditAction,
  BlockerSeverity,
  BlockerSource,
  DeviceOperationalStatus,
  DeviceReferenceType,
  LocationReadinessStatus,
  OperatingMode,
  RecordLifecycleStatus,
  SourceChannel,
  SpaceType,
  ZoneMemberType,
  EvidenceType,
  FacilityFaultStatus,
  FaultDismissalOutcome,
  FaultPriority,
  RetentionClass,
  WorkOrderStatus,
  WorkOrderType,
} from './enums';

/**
 * The S152 wire types, transcribed from the facilities service's OpenAPI document.
 *
 * The API client strips the `{data, error}` envelope, so everything here is the payload a screen
 * actually receives. Nothing is invented: a field absent from the service is absent here, and a
 * screen that needs one it does not have is recorded in the UI gap report rather than faked.
 */

/** The system-managed fields SRS-SFL-S152-01 requires on every operational record. */
export interface RecordMetadata {
  createdBy: string;
  createdAt: string;
  lastModifiedBy: string;
  lastModifiedAt: string;
  /** The optimistic lock. Send it back as `expectedVersion` to be told when a write is stale. */
  version: number;
  sourceChannel: SourceChannel;
  correlationId: string | null;
}

/** The paged envelope every S152 search endpoint returns. */
export interface FacilitiesPage<T> {
  items: T[];
  /** What the caller may see, after site-scope filtering — not what exists. */
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

// ---- estate ---------------------------------------------------------------------------------

export interface Site {
  id: string;
  siteCode: string;
  name: string;
  description: string | null;
  active: boolean;
  lifecycleStatus: RecordLifecycleStatus;
  operatingMode: OperatingMode;
  operatingModeChangedAt: string | null;
  operatingModeChangedBy: string | null;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface Building {
  id: string;
  siteId: string;
  siteCode: string;
  buildingCode: string;
  name: string;
  description: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface Floor {
  id: string;
  buildingId: string;
  siteCode: string;
  floorCode: string;
  name: string;
  /** Signed and nullable: basements sort below zero, mezzanines have no level at all. */
  levelNumber: number | null;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface Space {
  id: string;
  floorId: string;
  siteCode: string;
  roomCode: string;
  name: string;
  spaceType: SpaceType;
  /** The pre-S152 free-text type, written from `spaceType` and never edited independently. */
  roomType: string | null;
  capacity: number | null;
  areaSqm: number | null;
  costCentre: string | null;
  bookable: boolean;
  examinationCapable: boolean;
  /** Derived by the service — flag, lifecycle and readiness combined. Do not recompute. */
  availableForBooking: boolean;
  /** Stricter than booking: requires READY outright. */
  availableForExamination: boolean;
  readinessStatus: LocationReadinessStatus;
  readinessNotes: string | null;
  readinessUpdatedAt: string | null;
  readinessLocked: boolean;
  readinessLockedBy: string | null;
  readinessLockedAt: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface Zone {
  id: string;
  siteCode: string;
  zoneCode: string;
  name: string;
  purpose: string | null;
  parentZoneId: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface ZoneMember {
  id: string;
  zoneId: string;
  memberType: ZoneMemberType;
  memberId: string;
  siteCode: string;
  addedBy: string;
  addedAt: string;
}

export interface DeviceReference {
  id: string;
  siteCode: string;
  deviceCode: string;
  name: string;
  type: DeviceReferenceType;
  status: DeviceOperationalStatus;
  roomId: string | null;
  locationCode: string | null;
  vendor: string | null;
  externalReference: string | null;
  /** The vendor's observation time, not our receipt time — what staleness is measured from. */
  statusReportedAt: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

export interface FacilityAsset {
  id: string;
  siteCode: string;
  assetCode: string;
  name: string;
  category: AssetCategory;
  criticality: AssetCriticality;
  operationalStatus: AssetOperationalStatus;
  roomId: string | null;
  locationCode: string | null;
  manufacturer: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  installedOn: string | null;
  warrantyExpiresOn: string | null;
  serviceIntervalDays: number | null;
  lastServicedOn: string | null;
  /** Derived from the interval and the last service, or from installation where there is none. */
  serviceDueOn: string | null;
  custodian: string | null;
  deviceReferenceId: string | null;
  /** AVAMP-Lite's identifier for the same physical thing. A value, not a link we can follow. */
  assetReferenceId: string | null;
  statusNotes: string | null;
  statusChangedAt: string | null;
  /** True when this asset is currently raising a readiness blocker on the space it sits in. */
  impairsReadiness: boolean;
  lifecycleStatus: RecordLifecycleStatus;
  createdAt: string;
  metadata: RecordMetadata;
}

// ---- readiness ------------------------------------------------------------------------------

export interface ChecklistItem {
  id: string;
  itemCode: string;
  description: string;
  /** Declared on the item, not chosen by the assessor. This is what keeps two officers to one standard. */
  severityIfFailed: BlockerSeverity;
  mandatory: boolean;
  weight: number;
  sortOrder: number;
}

export interface ReadinessChecklist {
  id: string;
  siteCode: string;
  checklistCode: string;
  name: string;
  description: string | null;
  /** Null means the checklist applies to any space type. */
  spaceType: SpaceType | null;
  /** Null means it applies in any operating mode. */
  operatingMode: OperatingMode | null;
  version: number;
  totalWeight: number;
  items: ChecklistItem[];
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface AssessmentItem {
  id: string;
  itemCode: string;
  description: string;
  severityIfFailed: BlockerSeverity;
  mandatory: boolean;
  weight: number;
  passed: boolean;
  comment: string | null;
}

export interface ReadinessAssessment {
  id: string;
  roomId: string;
  siteCode: string;
  checklistId: string | null;
  checklistCode: string | null;
  /** The version the questions were asked at, so an old result stays readable. */
  checklistVersion: number;
  operatingMode: OperatingMode;
  outcome: LocationReadinessStatus;
  /** Weighted percentage of items passed. Reported beside the status, never driving it. */
  score: number;
  hasMandatoryFailure: boolean;
  items: AssessmentItem[];
  notes: string | null;
  assessedBy: string;
  assessedAt: string;
}

export interface ReadinessBlocker {
  id: string;
  roomId: string;
  siteCode: string;
  assessmentId: string | null;
  source: BlockerSource;
  sourceReference: string | null;
  severity: BlockerSeverity;
  description: string;
  raisedBy: string;
  raisedAt: string;
  resolved: boolean;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNotes: string | null;
}

/** A space's current readiness and the reasons for it — the S152-05 drilldown. */
export interface ReadinessOutcome {
  status: LocationReadinessStatus;
  score: number;
  summary: string;
  criticalCount: number;
  majorCount: number;
  minorCount: number;
  advisoryCount: number;
  openBlockers: ReadinessBlocker[];
}

// ---- dashboard ------------------------------------------------------------------------------

export interface DashboardSpaceReadiness {
  total: number;
  ready: number;
  degraded: number;
  blocked: number;
  unknown: number;
  bookable: number;
  availableForBooking: number;
  examinationCapable: number;
  availableForExamination: number;
}

export interface DashboardBlockerSummary {
  critical: number;
  major: number;
  minor: number;
  advisory: number;
  total: number;
  /** Critical blockers open longer than the configured escalation window. */
  criticalBeyondEscalationWindow: number;
}

export interface DashboardAssetSummary {
  total: number;
  impaired: number;
  criticalImpaired: number;
  serviceOverdue: number;
  serviceDueSoon: number;
  warrantyExpiringSoon: number;
}

export interface DashboardMaintenanceSummary {
  openFaults: number;
  openWorkOrders: number;
}

/** One row behind a count. The id is what a drilldown opens. */
export interface DashboardExceptionRow {
  id: string;
  resourceType: string;
  code: string;
  label: string;
  reason: string;
  severity: string;
}

export interface FacilityDashboard {
  siteCode: string;
  operatingMode: OperatingMode;
  generatedAt: string;
  spaces: DashboardSpaceReadiness;
  blockers: DashboardBlockerSummary;
  assets: DashboardAssetSummary;
  maintenance: DashboardMaintenanceSummary;
  readinessScore: number;
  /** True when any space's readiness is older than the configured threshold, or never assessed. */
  stale: boolean;
  staleWarning: string | null;
  examinationRisks: DashboardExceptionRow[];
  unavailableSpaces: DashboardExceptionRow[];
  staleReadiness: DashboardExceptionRow[];
}

// ---- governance -----------------------------------------------------------------------------

export interface AuditEvent {
  id: string;
  sequenceNo: number;
  siteScope: string;
  actorId: string;
  actorDisplayName: string;
  action: AuditAction;
  resourceType: string;
  resourceId: string;
  beforeValue: string | null;
  afterValue: string | null;
  correlationId: string | null;
  sourceChannel: SourceChannel;
  occurredAt: string;
  previousHash: string;
  recordHash: string;
}

export interface AuditChainVerification {
  intact: boolean;
  recordsVerified: number;
  brokenAtSequence: number | null;
  expected: string | null;
  found: string | null;
  reason: string | null;
  headHash: string | null;
}

export interface ConfigurationValue {
  key: string;
  /** Null is the platform default; a site code is an override for that site. */
  siteCode: string | null;
  value: string;
  valueType: string;
  description: string | null;
  version: number;
  updatedBy: string;
  updatedAt: string;
}

// ---- requests -------------------------------------------------------------------------------

export interface CreateSiteRequest {
  siteCode: string;
  name: string;
  description?: string | null;
}

export interface UpdateSiteRequest {
  name?: string | null;
  description?: string | null;
  expectedVersion?: number | null;
}

export interface ChangeLifecycleRequest {
  status: RecordLifecycleStatus;
  expectedVersion?: number | null;
}

export interface ChangeOperatingModeRequest {
  operatingMode: OperatingMode;
  reason?: string | null;
}

export interface CreateBuildingRequest {
  siteId: string;
  buildingCode: string;
  name: string;
  description?: string | null;
}

export interface CreateFloorRequest {
  buildingId: string;
  floorCode: string;
  name: string;
  levelNumber?: number | null;
}

export interface CreateSpaceRequest {
  floorId: string;
  roomCode: string;
  name: string;
  spaceType?: SpaceType | null;
  capacity?: number | null;
  areaSqm?: number | null;
  costCentre?: string | null;
  bookable?: boolean | null;
  examinationCapable?: boolean | null;
}

export interface UpdateSpaceRequest {
  name?: string | null;
  spaceType?: SpaceType | null;
  capacity?: number | null;
  areaSqm?: number | null;
  costCentre?: string | null;
  bookable?: boolean | null;
  examinationCapable?: boolean | null;
  expectedVersion?: number | null;
}

export interface UpdateSpaceReadinessRequest {
  status: LocationReadinessStatus;
  notes?: string | null;
}

export interface CreateZoneRequest {
  siteCode: string;
  zoneCode: string;
  name: string;
  purpose?: string | null;
  parentZoneId?: string | null;
}

export interface AddZoneMemberRequest {
  memberType: ZoneMemberType;
  memberId: string;
}

export interface RegisterDeviceReferenceRequest {
  siteCode: string;
  deviceCode: string;
  name: string;
  type: DeviceReferenceType;
  roomId?: string | null;
  locationCode?: string | null;
  vendor?: string | null;
  externalReference?: string | null;
}

export interface RegisterAssetRequest {
  siteCode: string;
  assetCode: string;
  name: string;
  category: AssetCategory;
  criticality?: AssetCriticality | null;
  roomId?: string | null;
  locationCode?: string | null;
  manufacturer?: string | null;
  modelNumber?: string | null;
  serialNumber?: string | null;
  installedOn?: string | null;
  warrantyExpiresOn?: string | null;
  serviceIntervalDays?: number | null;
  custodian?: string | null;
  deviceReferenceId?: string | null;
  assetReferenceId?: string | null;
}

export interface UpdateAssetRequest {
  name?: string | null;
  category?: AssetCategory | null;
  criticality?: AssetCriticality | null;
  manufacturer?: string | null;
  modelNumber?: string | null;
  serialNumber?: string | null;
  warrantyExpiresOn?: string | null;
  serviceIntervalDays?: number | null;
  custodian?: string | null;
  expectedVersion?: number | null;
}

export interface ChangeAssetStatusRequest {
  operationalStatus: AssetOperationalStatus;
  notes?: string | null;
  expectedVersion?: number | null;
}

export interface RelocateAssetRequest {
  roomId?: string | null;
  locationCode?: string | null;
  expectedVersion?: number | null;
}

export interface ChecklistItemRequest {
  itemCode: string;
  description: string;
  severityIfFailed: BlockerSeverity;
  mandatory?: boolean | null;
  weight?: number | null;
  sortOrder?: number | null;
}

export interface CreateChecklistRequest {
  siteCode: string;
  checklistCode: string;
  name: string;
  description?: string | null;
  spaceType?: SpaceType | null;
  operatingMode?: OperatingMode | null;
  items: ChecklistItemRequest[];
}

export interface UpdateChecklistRequest {
  name?: string | null;
  description?: string | null;
  /** Supplying any replaces them all and bumps the version. Omit to leave the questions alone. */
  items?: ChecklistItemRequest[] | null;
  expectedVersion?: number | null;
}

export interface AssessmentAnswerRequest {
  itemCode: string;
  passed: boolean;
  comment?: string | null;
}

export interface SubmitAssessmentRequest {
  roomId: string;
  /** Omit to let the service resolve the checklist from the space type and operating mode. */
  checklistId?: string | null;
  answers: AssessmentAnswerRequest[];
  notes?: string | null;
}

export interface RaiseBlockerRequest {
  roomId: string;
  severity: BlockerSeverity;
  description: string;
}

export interface ResolveBlockerRequest {
  resolutionNotes: string;
}

export interface PutConfigurationRequest {
  value: string;
  valueType?: string | null;
  description?: string | null;
  siteCode?: string | null;
}

// ---- query parameters -----------------------------------------------------------------------

export interface SpaceSearchParams {
  siteCode?: string;
  buildingId?: string;
  floorId?: string;
  spaceType?: SpaceType;
  readinessStatus?: LocationReadinessStatus;
  bookable?: boolean;
  examinationCapable?: boolean;
  page?: number;
  size?: number;
}

export interface AssetSearchParams {
  siteCode?: string;
  roomId?: string;
  category?: AssetCategory;
  criticality?: AssetCriticality;
  operationalStatus?: AssetOperationalStatus;
  page?: number;
  size?: number;
}

export interface BlockerSearchParams {
  siteCode?: string;
  roomId?: string;
  severity?: BlockerSeverity;
  open?: boolean;
  limit?: number;
}

export interface AuditSearchParams {
  siteCode?: string;
  resourceType?: string;
  resourceId?: string;
  actorId?: string;
  action?: AuditAction;
  from?: string;
  to?: string;
  limit?: number;
}

// =================================================================================================
// S153 CMMS — transcribed from the service's OpenAPI document
//
// Several fields here are derived by the service and must not be recomputed on this side:
// `overdue`, `minutesOverdue`, `open`, `assignable`, `dueForGeneration`, `disposalEligibleFrom`
// and `supportsClosure`. A client that works them out again is one that will eventually disagree
// with the escalation sweep about whether something is late.
// =================================================================================================

export interface FacilityFault {
  id: string;
  faultNumber: string;
  siteCode: string;
  /** Null when the fault is somewhere the estate model has no room for — a corridor, a car park. */
  roomId: string | null;
  locationCode: string | null;
  assetId: string | null;
  title: string;
  description: string;
  category: string | null;
  priority: FaultPriority;
  status: FacilityFaultStatus;
  /** Derived from the status by the service. */
  open: boolean;
  reportedBy: string;
  reportedAt: string;
  triagedBy: string | null;
  triagedAt: string | null;
  triageNotes: string | null;
  duplicateOfFaultId: string | null;
  workOrderId: string | null;
  /** Null until triaged: an untriaged fault has no confirmed priority, and so no deadline. */
  slaDueAt: string | null;
  overdue: boolean;
  escalationLevel: number;
  escalatedAt: string | null;
  /** Whether this fault currently holds a readiness blocker open on its space. */
  blockerRaised: boolean;
  resolvedAt: string | null;
  resolutionNotes: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface WorkOrder {
  id: string;
  workOrderNumber: string;
  workOrderType: WorkOrderType;
  facilityFaultId: string | null;
  faultNumber: string | null;
  scheduleId: string | null;
  siteCode: string;
  roomId: string | null;
  locationCode: string | null;
  assetId: string | null;
  title: string;
  description: string | null;
  priority: FaultPriority;
  status: WorkOrderStatus;
  open: boolean;
  assignedTo: string | null;
  vendorId: string | null;
  assignedAt: string | null;
  startedAt: string | null;
  holdReason: string | null;
  heldAt: string | null;
  /** Accumulated hold time. Reported beside the deadline, never subtracted from it. */
  totalHeldSeconds: number;
  slaDueAt: string | null;
  overdue: boolean;
  minutesOverdue: number | null;
  escalationLevel: number;
  escalatedAt: string | null;
  /** How many evidence items closure needs, fixed when the order was raised. */
  evidenceRequired: number;
  completedAt: string | null;
  completionNotes: string | null;
  closureNotes: string | null;
  closedBy: string | null;
  closedAt: string | null;
  cancellationReason: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface WorkOrderPart {
  id: string;
  workOrderId: string;
  partCode: string;
  description: string;
  quantity: number;
  unitCost: number | null;
  /** Quantity times unit cost, or null when no cost was recorded. */
  lineCost: number | null;
  currency: string;
  supplier: string | null;
  recordedBy: string;
  recordedAt: string;
}

export interface MaintenanceEvidence {
  id: string;
  workOrderId: string;
  siteCode: string;
  evidenceType: EvidenceType;
  /** Where the file is in object storage. This service never holds the bytes. */
  fileReference: string;
  fileName: string | null;
  mediaType: string | null;
  sizeBytes: number | null;
  contentHash: string;
  retentionClass: RetentionClass;
  legalHold: boolean;
  /** Null while a legal hold is in force, which is how a hold reads to a client. */
  disposalEligibleFrom: string | null;
  /** False for an invoice: it proves money was spent, not that the work was done. */
  supportsClosure: boolean;
  notes: string | null;
  uploadedBy: string;
  uploadedAt: string;
}

export interface EvidenceExportGrant {
  evidenceId: string;
  fileReference: string;
  contentHash: string;
  retentionClass: RetentionClass;
  recipient: string;
  reason: string;
  approvedBy: string;
  approvedAt: string;
}

export interface MaintenanceVendor {
  id: string;
  siteCode: string;
  vendorCode: string;
  name: string;
  specialisation: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  /** The contracted hours to respond. Wins over the priority rule when it is tighter. */
  responseHours: number | null;
  contractReference: string | null;
  contractExpiresOn: string | null;
  /** Procurement's identifier for the same company. A value, not a link this service follows. */
  externalVendorId: string | null;
  assignable: boolean;
  unassignableReason: string | null;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface PreventiveSchedule {
  id: string;
  siteCode: string;
  scheduleCode: string;
  name: string;
  description: string | null;
  assetId: string;
  roomId: string | null;
  intervalDays: number;
  leadTimeDays: number;
  priority: FaultPriority;
  workOrderType: WorkOrderType;
  nextDueOn: string;
  /** The date the work order is raised on — the due date minus the lead time. */
  generateOn: string;
  lastGeneratedFor: string | null;
  lastGeneratedAt: string | null;
  lastWorkOrderId: string | null;
  dueForGeneration: boolean;
  lifecycleStatus: RecordLifecycleStatus;
  metadata: RecordMetadata;
}

export interface EscalationSweep {
  evaluatedAt: string;
  faultsEscalated: number;
  workOrdersEscalated: number;
  total: number;
}

export interface GenerationRun {
  generatedFor: string;
  workOrdersRaised: number;
  workOrders: WorkOrder[];
}

// ---- requests -----------------------------------------------------------------------------------

export interface ReportFaultRequest {
  siteCode?: string;
  roomId?: string | null;
  locationCode?: string | null;
  assetId?: string | null;
  title: string;
  description: string;
  category?: string | null;
  priority: FaultPriority;
}

export interface TriageFaultRequest {
  priority?: FaultPriority;
  notes?: string | null;
  expectedVersion?: number | null;
}

export interface DismissFaultRequest {
  outcome: FaultDismissalOutcome;
  reason: string;
  duplicateOfFaultId?: string | null;
  expectedVersion?: number | null;
}

export interface CreateWorkOrderRequest {
  facilityFaultId: string;
  vendorId?: string | null;
  assignTo?: string | null;
}

export interface AssignWorkOrderRequest {
  assignedTo: string;
  vendorId?: string | null;
  expectedVersion?: number | null;
}

/** Start, hold, complete and reopen. The service requires notes for hold and reopen. */
export interface TransitionWorkOrderRequest {
  notes?: string | null;
  expectedVersion?: number | null;
}

export interface CloseWorkOrderRequest {
  closureNotes: string;
  expectedVersion?: number | null;
}

export interface CancelWorkOrderRequest {
  reason: string;
  expectedVersion?: number | null;
}

export interface RecordPartRequest {
  partCode: string;
  description: string;
  quantity: number;
  unitCost?: number | null;
  currency?: string | null;
  supplier?: string | null;
}

export interface AttachEvidenceRequest {
  evidenceType: EvidenceType;
  fileReference: string;
  fileName?: string | null;
  mediaType?: string | null;
  sizeBytes?: number | null;
  /** 64-character hex SHA-256. Rejected at the edge as well as in the domain. */
  contentHash: string;
  retentionClass: RetentionClass;
  notes?: string | null;
}

export interface ExportEvidenceRequest {
  reason: string;
  recipient: string;
}

export interface SetLegalHoldRequest {
  legalHold: boolean;
  reason: string;
}

export interface RegisterVendorRequest {
  siteCode: string;
  vendorCode: string;
  name: string;
  specialisation?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  responseHours?: number | null;
  contractReference?: string | null;
  contractExpiresOn?: string | null;
  externalVendorId?: string | null;
}

export interface UpdateVendorRequest {
  name?: string | null;
  specialisation?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  responseHours?: number | null;
  contractReference?: string | null;
  contractExpiresOn?: string | null;
  expectedVersion?: number | null;
}

export interface CreateScheduleRequest {
  siteCode: string;
  scheduleCode: string;
  name: string;
  description?: string | null;
  assetId: string;
  intervalDays: number;
  leadTimeDays: number;
  priority: FaultPriority;
  workOrderType: WorkOrderType;
  firstDueOn: string;
}

export interface UpdateScheduleRequest {
  name?: string | null;
  description?: string | null;
  intervalDays?: number | null;
  leadTimeDays?: number | null;
  priority?: FaultPriority | null;
  nextDueOn?: string | null;
  expectedVersion?: number | null;
}

// ---- search params ------------------------------------------------------------------------------

export interface FaultSearchParams {
  siteCode?: string;
  roomId?: string;
  status?: FacilityFaultStatus;
  openOnly?: boolean;
  limit?: number;
}

export interface WorkOrderSearchParams {
  siteCode?: string;
  roomId?: string;
  assetId?: string;
  status?: WorkOrderStatus;
  assignedTo?: string;
  vendorId?: string;
  openOnly?: boolean;
  limit?: number;
}
