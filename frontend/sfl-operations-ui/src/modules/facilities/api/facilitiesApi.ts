import { apiClient } from 'shared/api/client';
import type { QueryParams } from 'shared/api/types';
import type {
  AddZoneMemberRequest,
  AssetSearchParams,
  AuditChainVerification,
  AuditEvent,
  AuditSearchParams,
  Building,
  ChangeAssetStatusRequest,
  ChangeLifecycleRequest,
  ChangeOperatingModeRequest,
  ConfigurationValue,
  CreateBuildingRequest,
  CreateChecklistRequest,
  CreateFloorRequest,
  CreateSiteRequest,
  CreateSpaceRequest,
  CreateZoneRequest,
  DashboardExceptionRow,
  DeviceReference,
  FacilitiesPage,
  FacilityAsset,
  FacilityDashboard,
  Floor,
  PutConfigurationRequest,
  RaiseBlockerRequest,
  ReadinessAssessment,
  ReadinessBlocker,
  ReadinessChecklist,
  ReadinessOutcome,
  RegisterAssetRequest,
  RegisterDeviceReferenceRequest,
  RelocateAssetRequest,
  ResolveBlockerRequest,
  Site,
  Space,
  SpaceSearchParams,
  SubmitAssessmentRequest,
  UpdateAssetRequest,
  UpdateChecklistRequest,
  UpdateSiteRequest,
  UpdateSpaceReadinessRequest,
  UpdateSpaceRequest,
  Zone,
  ZoneMember,
  AssignWorkOrderRequest,
  AttachEvidenceRequest,
  CancelWorkOrderRequest,
  CloseWorkOrderRequest,
  CreateScheduleRequest,
  CreateWorkOrderRequest,
  DismissFaultRequest,
  EscalationSweep,
  EvidenceExportGrant,
  ExportEvidenceRequest,
  FacilityFault,
  FaultSearchParams,
  GenerationRun,
  MaintenanceEvidence,
  MaintenanceVendor,
  PreventiveSchedule,
  RecordPartRequest,
  RegisterVendorRequest,
  ReportFaultRequest,
  SetLegalHoldRequest,
  TransitionWorkOrderRequest,
  TriageFaultRequest,
  UpdateScheduleRequest,
  UpdateVendorRequest,
  WorkOrder,
  WorkOrderPart,
  WorkOrderSearchParams,
  BlockerSearchParams,
} from './dto';
import type { DeviceReferenceType } from './enums';

/**
 * The S152 API surface.
 *
 * One function per endpoint, all addressed to the `facilities` service — S152 today, S153 and S159
 * behind the same origin later. The shared client handles the actor headers, the correlation ID, the
 * `{data, error}` envelope and the error catalogue, so nothing here touches `fetch`.
 *
 * `idempotent: true` is set on exactly the state-**creating** POSTs the service marks as accepting an
 * `Idempotency-Key`. It is deliberately absent from PATCH transitions: those are already guarded by
 * the record's version and its state machine, so a repeated PATCH is either a no-op or an
 * invalid-transition error, and a key there would be ceremony without a failure mode.
 */

const base = '/api/v1/facilities';
const service = 'facilities' as const;

const get = <T>(path: string, query?: QueryParams, signal?: AbortSignal) =>
  apiClient.get<T>(`${base}${path}`, query, signal, service);

const post = <T>(path: string, body?: unknown, idempotent = false) =>
  apiClient.post<T>(`${base}${path}`, body, { service, idempotent });

const patch = <T>(path: string, body?: unknown) =>
  apiClient.patch<T>(`${base}${path}`, body, { service });

const put = <T>(path: string, body?: unknown) =>
  apiClient.put<T>(`${base}${path}`, body, { service });

// ---- sites ------------------------------------------------------------------------------------

export const listSites = (signal?: AbortSignal) => get<Site[]>('/sites', undefined, signal);

export const getSite = (siteId: string, signal?: AbortSignal) =>
  get<Site>(`/sites/${siteId}`, undefined, signal);

export const createSite = (request: CreateSiteRequest) => post<Site>('/sites', request, true);

export const updateSite = (siteId: string, request: UpdateSiteRequest) =>
  patch<Site>(`/sites/${siteId}`, request);

export const changeSiteLifecycle = (siteId: string, request: ChangeLifecycleRequest) =>
  patch<Site>(`/sites/${siteId}/lifecycle`, request);

/** Declaring or standing down examination mode. Audited, and refused if it is a no-op. */
export const changeOperatingMode = (siteId: string, request: ChangeOperatingModeRequest) =>
  patch<Site>(`/sites/${siteId}/operating-mode`, request);

// ---- buildings and floors ---------------------------------------------------------------------

export const listBuildings = (siteCode?: string, signal?: AbortSignal) =>
  get<Building[]>('/buildings', { siteCode }, signal);

export const getBuilding = (buildingId: string, signal?: AbortSignal) =>
  get<Building>(`/buildings/${buildingId}`, undefined, signal);

export const createBuilding = (request: CreateBuildingRequest) =>
  post<Building>('/buildings', request, true);

export const listFloors = (buildingId: string, signal?: AbortSignal) =>
  get<Floor[]>(`/buildings/${buildingId}/floors`, undefined, signal);

export const getFloor = (floorId: string, signal?: AbortSignal) =>
  get<Floor>(`/floors/${floorId}`, undefined, signal);

export const createFloor = (request: CreateFloorRequest) => post<Floor>('/floors', request, true);

// ---- spaces -----------------------------------------------------------------------------------

/** The plain list. Kept because the pre-S152 facilities page reads this shape. */
export const listSpaces = (siteCode?: string, signal?: AbortSignal) =>
  get<Space[]>('/rooms', { siteCode }, signal);

export const searchSpaces = (params: SpaceSearchParams, signal?: AbortSignal) =>
  get<FacilitiesPage<Space>>('/rooms/search', params as QueryParams, signal);

export const getSpace = (roomId: string, signal?: AbortSignal) =>
  get<Space>(`/rooms/${roomId}`, undefined, signal);

export const createSpace = (request: CreateSpaceRequest) => post<Space>('/rooms', request, true);

export const updateSpace = (roomId: string, request: UpdateSpaceRequest) =>
  patch<Space>(`/rooms/${roomId}`, request);

export const changeSpaceLifecycle = (roomId: string, request: ChangeLifecycleRequest) =>
  patch<Space>(`/rooms/${roomId}/lifecycle`, request);

/**
 * Sets a space's readiness by hand.
 *
 * Still subject to the critical-blocker rule: asking for READY while one is open is refused with
 * `READINESS_BLOCKED`. This overrides the process, never the invariant.
 */
export const updateSpaceReadiness = (roomId: string, request: UpdateSpaceReadinessRequest) =>
  patch<Space>(`/rooms/${roomId}/readiness`, request);

// ---- zones ------------------------------------------------------------------------------------

export const listZones = (siteCode?: string, signal?: AbortSignal) =>
  get<Zone[]>('/zones', { siteCode }, signal);

export const getZone = (zoneId: string, signal?: AbortSignal) =>
  get<Zone>(`/zones/${zoneId}`, undefined, signal);

export const createZone = (request: CreateZoneRequest) => post<Zone>('/zones', request, true);

export const listZoneMembers = (zoneId: string, signal?: AbortSignal) =>
  get<ZoneMember[]>(`/zones/${zoneId}/members`, undefined, signal);

export const addZoneMember = (zoneId: string, request: AddZoneMemberRequest) =>
  post<ZoneMember>(`/zones/${zoneId}/members`, request);

export const removeZoneMember = (zoneId: string, memberType: string, memberId: string) =>
  apiClient.delete<void>(`${base}/zones/${zoneId}/members/${memberType}/${memberId}`, { service });

// ---- device references ------------------------------------------------------------------------

export const listDeviceReferences = (
  params: { siteCode?: string; type?: DeviceReferenceType; roomId?: string },
  signal?: AbortSignal,
) => get<DeviceReference[]>('/device-references', params as QueryParams, signal);

export const getDeviceReference = (deviceId: string, signal?: AbortSignal) =>
  get<DeviceReference>(`/device-references/${deviceId}`, undefined, signal);

export const registerDeviceReference = (request: RegisterDeviceReferenceRequest) =>
  post<DeviceReference>('/device-references', request, true);

// ---- facility assets --------------------------------------------------------------------------

export const searchAssets = (params: AssetSearchParams, signal?: AbortSignal) =>
  get<FacilitiesPage<FacilityAsset>>('/assets', params as QueryParams, signal);

export const getAsset = (assetId: string, signal?: AbortSignal) =>
  get<FacilityAsset>(`/assets/${assetId}`, undefined, signal);

export const registerAsset = (request: RegisterAssetRequest) =>
  post<FacilityAsset>('/assets', request, true);

export const updateAsset = (assetId: string, request: UpdateAssetRequest) =>
  patch<FacilityAsset>(`/assets/${assetId}`, request);

/** Recomputes the readiness of the space the asset sits in. That is the point of the call. */
export const changeAssetStatus = (assetId: string, request: ChangeAssetStatusRequest) =>
  patch<FacilityAsset>(`/assets/${assetId}/status`, request);

export const relocateAsset = (assetId: string, request: RelocateAssetRequest) =>
  patch<FacilityAsset>(`/assets/${assetId}/location`, request);

// ---- readiness --------------------------------------------------------------------------------

export const listChecklists = (siteCode?: string, signal?: AbortSignal) =>
  get<ReadinessChecklist[]>('/readiness/checklists', { siteCode }, signal);

export const getChecklist = (checklistId: string, signal?: AbortSignal) =>
  get<ReadinessChecklist>(`/readiness/checklists/${checklistId}`, undefined, signal);

export const createChecklist = (request: CreateChecklistRequest) =>
  post<ReadinessChecklist>('/readiness/checklists', request, true);

export const updateChecklist = (checklistId: string, request: UpdateChecklistRequest) =>
  patch<ReadinessChecklist>(`/readiness/checklists/${checklistId}`, request);

export const listAssessments = (
  params: { siteCode?: string; roomId?: string; limit?: number },
  signal?: AbortSignal,
) => get<ReadinessAssessment[]>('/readiness/assessments', params as QueryParams, signal);

export const getAssessment = (assessmentId: string, signal?: AbortSignal) =>
  get<ReadinessAssessment>(`/readiness/assessments/${assessmentId}`, undefined, signal);

export const submitAssessment = (request: SubmitAssessmentRequest) =>
  post<ReadinessAssessment>('/readiness/assessments', request, true);

export const listBlockers = (params: BlockerSearchParams, signal?: AbortSignal) =>
  get<ReadinessBlocker[]>('/readiness/blockers', params as QueryParams, signal);

export const raiseBlocker = (request: RaiseBlockerRequest) =>
  post<ReadinessBlocker>('/readiness/blockers', request);

/** Resolving the last open critical blocker is what lets a space become READY again. */
export const resolveBlocker = (blockerId: string, request: ResolveBlockerRequest) =>
  patch<ReadinessBlocker>(`/readiness/blockers/${blockerId}/resolution`, request);

export const getSpaceReadiness = (roomId: string, signal?: AbortSignal) =>
  get<ReadinessOutcome>(`/readiness/rooms/${roomId}`, undefined, signal);

export const lockSpaceReadiness = (roomId: string, reason?: string) =>
  post<Space>(`/readiness/rooms/${roomId}/lock`, { reason });

export const unlockSpaceReadiness = (roomId: string, reason?: string) =>
  apiClient.delete<Space>(`${base}/readiness/rooms/${roomId}/lock`, {
    service,
    query: { reason },
  });

// ---- dashboard --------------------------------------------------------------------------------

export const getDashboard = (siteCode?: string, signal?: AbortSignal) =>
  get<FacilityDashboard>('/dashboard', { siteCode }, signal);

export const getDashboardBlockers = (siteCode?: string, signal?: AbortSignal) =>
  get<DashboardExceptionRow[]>('/dashboard/blockers', { siteCode }, signal);

export const getDashboardUnavailable = (siteCode?: string, signal?: AbortSignal) =>
  get<DashboardExceptionRow[]>('/dashboard/unavailable', { siteCode }, signal);

export const getDashboardStale = (siteCode?: string, signal?: AbortSignal) =>
  get<DashboardExceptionRow[]>('/dashboard/stale', { siteCode }, signal);

// ---- governance -------------------------------------------------------------------------------

export const searchAudit = (params: AuditSearchParams, signal?: AbortSignal) =>
  get<AuditEvent[]>('/audit', params as QueryParams, signal);

/** Replays the whole chain. Running the check is itself audited. */
export const verifyAuditChain = (signal?: AbortSignal) =>
  get<AuditChainVerification>('/audit/integrity', undefined, signal);

export const listConfiguration = (siteCode?: string, signal?: AbortSignal) =>
  get<ConfigurationValue[]>('/configuration', { siteCode }, signal);

export const putConfiguration = (key: string, request: PutConfigurationRequest) =>
  put<ConfigurationValue>(`/configuration/${key}`, request);

/**
 * The calling actor's facilities permissions, as a flat list.
 *
 * Shaped like the fleet and emergency equivalents so `shared/layout/actorPermissions` reads all
 * three with one loader. The dashboard calls it through that loader, not directly.
 */
export const getActorPermissions = (signal?: AbortSignal) =>
  get<string[]>('/actor/permissions', undefined, signal);

// =================================================================================================
// S153 CMMS
//
// Same service, same client, same envelope. `idempotent: true` appears on exactly the four
// state-creating POSTs the service marks as accepting an `Idempotency-Key` — reporting a fault,
// raising a work order, attaching evidence, registering a vendor or schedule. It is deliberately
// absent from every PATCH: those are guarded by the record's version and its state machine, so a
// repeat is either a no-op or an invalid-transition error, and a key there would be ceremony.
// =================================================================================================

// ---- faults -------------------------------------------------------------------------------------

export const searchFaults = (params: FaultSearchParams, signal?: AbortSignal) =>
  get<FacilityFault[]>('/faults', params as QueryParams, signal);

export const getFault = (faultId: string, signal?: AbortSignal) =>
  get<FacilityFault>(`/faults/${faultId}`, undefined, signal);

/** Open faults on one space, for the S152 space-detail screen. */
export const listFaultsForRoom = (roomId: string, signal?: AbortSignal) =>
  get<FacilityFault[]>(`/faults/rooms/${roomId}`, undefined, signal);

export const reportFault = (request: ReportFaultRequest) =>
  post<FacilityFault>('/faults', request, true);

/** Confirms the priority and starts the SLA clock. The only place priority may change. */
export const triageFault = (faultId: string, request: TriageFaultRequest) =>
  patch<FacilityFault>(`/faults/${faultId}/triage`, request);

export const dismissFault = (faultId: string, request: DismissFaultRequest) =>
  patch<FacilityFault>(`/faults/${faultId}/dismissal`, request);

export const changeFaultLifecycle = (faultId: string, request: ChangeLifecycleRequest) =>
  patch<FacilityFault>(`/faults/${faultId}/lifecycle`, request);

// ---- work orders --------------------------------------------------------------------------------

export const searchWorkOrders = (params: WorkOrderSearchParams, signal?: AbortSignal) =>
  get<WorkOrder[]>('/work-orders', params as QueryParams, signal);

export const getWorkOrder = (workOrderId: string, signal?: AbortSignal) =>
  get<WorkOrder>(`/work-orders/${workOrderId}`, undefined, signal);

export const createWorkOrderFromFault = (request: CreateWorkOrderRequest) =>
  post<WorkOrder>('/work-orders/from-fault', request, true);

/** Assignment and reassignment are the same call. Assigning releases a hold. */
export const assignWorkOrder = (workOrderId: string, request: AssignWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/assignment`, request);

export const startWorkOrder = (workOrderId: string, request: TransitionWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/start`, request);

export const holdWorkOrder = (workOrderId: string, request: TransitionWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/hold`, request);

export const completeWorkOrder = (workOrderId: string, request: TransitionWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/completion`, request);

/** Reverses a completion, and so takes the closing permission rather than the updating one. */
export const reopenWorkOrder = (workOrderId: string, request: TransitionWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/reopen`, request);

/** Refused without a reason, and without the evidence the order required when it was raised. */
export const closeWorkOrder = (workOrderId: string, request: CloseWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/closure`, request);

export const cancelWorkOrder = (workOrderId: string, request: CancelWorkOrderRequest) =>
  patch<WorkOrder>(`/work-orders/${workOrderId}/cancellation`, request);

// ---- parts --------------------------------------------------------------------------------------

export const listParts = (workOrderId: string, signal?: AbortSignal) =>
  get<WorkOrderPart[]>(`/work-orders/${workOrderId}/parts`, undefined, signal);

export const recordPart = (workOrderId: string, request: RecordPartRequest) =>
  post<WorkOrderPart>(`/work-orders/${workOrderId}/parts`, request);

export const removePart = (workOrderId: string, partId: string) =>
  apiClient.delete<void>(`${base}/work-orders/${workOrderId}/parts/${partId}`, { service });

// ---- evidence -----------------------------------------------------------------------------------

export const listEvidence = (workOrderId: string, signal?: AbortSignal) =>
  get<MaintenanceEvidence[]>(`/work-orders/${workOrderId}/evidence`, undefined, signal);

/** By reference. The file goes to object storage; this records where it is and what it hashed to. */
export const attachEvidence = (workOrderId: string, request: AttachEvidenceRequest) =>
  post<MaintenanceEvidence>(`/work-orders/${workOrderId}/evidence`, request, true);

export const getEvidence = (evidenceId: string, signal?: AbortSignal) =>
  get<MaintenanceEvidence>(`/maintenance-evidence/${evidenceId}`, undefined, signal);

/** Authorises and records an export. It does not move the file — it returns the reference to fetch. */
export const exportEvidence = (evidenceId: string, request: ExportEvidenceRequest) =>
  post<EvidenceExportGrant>(`/maintenance-evidence/${evidenceId}/exports`, request);

export const setEvidenceLegalHold = (evidenceId: string, request: SetLegalHoldRequest) =>
  patch<MaintenanceEvidence>(`/maintenance-evidence/${evidenceId}/legal-hold`, request);

// ---- vendors ------------------------------------------------------------------------------------

export const listVendors = (siteCode?: string, signal?: AbortSignal) =>
  get<MaintenanceVendor[]>('/maintenance/vendors', { siteCode }, signal);

export const getVendor = (vendorId: string, signal?: AbortSignal) =>
  get<MaintenanceVendor>(`/maintenance/vendors/${vendorId}`, undefined, signal);

export const registerVendor = (request: RegisterVendorRequest) =>
  post<MaintenanceVendor>('/maintenance/vendors', request, true);

export const updateVendor = (vendorId: string, request: UpdateVendorRequest) =>
  patch<MaintenanceVendor>(`/maintenance/vendors/${vendorId}`, request);

export const changeVendorLifecycle = (vendorId: string, request: ChangeLifecycleRequest) =>
  patch<MaintenanceVendor>(`/maintenance/vendors/${vendorId}/lifecycle`, request);

// ---- preventive schedules -------------------------------------------------------------------------

export const listSchedules = (
  params: { siteCode?: string; assetId?: string },
  signal?: AbortSignal,
) => get<PreventiveSchedule[]>('/maintenance/schedules', params as QueryParams, signal);

export const getSchedule = (scheduleId: string, signal?: AbortSignal) =>
  get<PreventiveSchedule>(`/maintenance/schedules/${scheduleId}`, undefined, signal);

export const createSchedule = (request: CreateScheduleRequest) =>
  post<PreventiveSchedule>('/maintenance/schedules', request, true);

export const updateSchedule = (scheduleId: string, request: UpdateScheduleRequest) =>
  patch<PreventiveSchedule>(`/maintenance/schedules/${scheduleId}`, request);

export const changeScheduleLifecycle = (scheduleId: string, request: ChangeLifecycleRequest) =>
  patch<PreventiveSchedule>(`/maintenance/schedules/${scheduleId}/lifecycle`, request);

// ---- sweeps ---------------------------------------------------------------------------------------

/**
 * Runs preventive generation on demand — what the scheduler does, when somebody asks.
 *
 * Idempotent by cycle: a schedule already generated for its current due date produces nothing,
 * however often this is called. No `Idempotency-Key` is needed or accepted for that reason.
 */
export const runGeneration = () =>
  post<GenerationRun>('/maintenance/schedules/runs', {});

/** Runs the SLA sweep on demand. Evaluated against the configuration active right now. */
export const runEscalationSweep = () =>
  post<EscalationSweep>('/maintenance/escalations/runs', {});
