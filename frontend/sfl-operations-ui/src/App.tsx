import { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router';
import { Spinner } from 'shared/components/DataState';
import { NotifierProvider } from 'shared/components/Notifier';
import AppShell from 'shared/layout/AppShell';
import RequireEntitlement, { NoProgrammePage } from 'shared/layout/RequireEntitlement';
import type { SystemCode } from 'shared/layout/programmes';
import { landingPath } from 'shared/layout/navigation';
import NotFoundPage from 'shared/pages/NotFoundPage';
import ScrollToTop from 'shared/layout/ScrollToTop';

const FacilitiesDashboardPage = lazy(() => import('modules/facilities/pages/FacilitiesDashboardPage'));
// S153 CMMS
const FaultRegisterPage = lazy(() => import('modules/facilities/pages/FaultRegisterPage'));
const FaultDetailPage = lazy(() => import('modules/facilities/pages/FaultDetailPage'));
const WorkOrderQueuePage = lazy(() => import('modules/facilities/pages/WorkOrderQueuePage'));
const WorkOrderDetailPage = lazy(() => import('modules/facilities/pages/WorkOrderDetailPage'));
const PreventiveSchedulesPage = lazy(() => import('modules/facilities/pages/PreventiveSchedulesPage'));
const ScheduleDetailPage = lazy(() => import('modules/facilities/pages/ScheduleDetailPage'));
const MaintenanceVendorsPage = lazy(() => import('modules/facilities/pages/MaintenanceVendorsPage'));
const EvidenceDetailPage = lazy(() => import('modules/facilities/pages/EvidenceDetailPage'));
const SiteRegisterPage = lazy(() => import('modules/facilities/pages/SiteRegisterPage'));
const SiteDetailPage = lazy(() => import('modules/facilities/pages/SiteDetailPage'));
const SpaceRegisterPage = lazy(() => import('modules/facilities/pages/SpaceRegisterPage'));
const SpaceDetailPage = lazy(() => import('modules/facilities/pages/SpaceDetailPage'));
const AssetRegisterPage = lazy(() => import('modules/facilities/pages/AssetRegisterPage'));
const AssetDetailPage = lazy(() => import('modules/facilities/pages/AssetDetailPage'));
const ZonesPage = lazy(() => import('modules/facilities/pages/ZonesPage'));
const DeviceReferencesPage = lazy(() => import('modules/facilities/pages/DeviceReferencesPage'));
const ReadinessAssessmentsPage = lazy(() => import('modules/facilities/pages/ReadinessAssessmentsPage'));
const ReadinessAssessmentDetailPage = lazy(
  () => import('modules/facilities/pages/ReadinessAssessmentDetailPage'),
);
const ReadinessChecklistsPage = lazy(() => import('modules/facilities/pages/ReadinessChecklistsPage'));
const ReadinessChecklistDetailPage = lazy(
  () => import('modules/facilities/pages/ReadinessChecklistDetailPage'),
);
const FacilitiesAuditPage = lazy(() => import('modules/facilities/pages/FacilitiesAuditPage'));
const FacilitiesConfigurationPage = lazy(
  () => import('modules/facilities/pages/FacilitiesConfigurationPage'),
);

const FleetDashboardPage = lazy(() => import('modules/fleet/pages/FleetDashboardPage'));
const VehicleRegisterPage = lazy(() => import('modules/fleet/pages/VehicleRegisterPage'));
const VehicleDetailPage = lazy(() => import('modules/fleet/pages/VehicleDetailPage'));
const DriverRegisterPage = lazy(() => import('modules/fleet/pages/DriverRegisterPage'));
const DriverDetailPage = lazy(() => import('modules/fleet/pages/DriverDetailPage'));
const TripQueuePage = lazy(() => import('modules/fleet/pages/TripQueuePage'));
const TripDetailPage = lazy(() => import('modules/fleet/pages/TripDetailPage'));
const WorkflowQueuePage = lazy(() => import('modules/fleet/pages/WorkflowQueuePage'));
const WorkflowDetailPage = lazy(() => import('modules/fleet/pages/WorkflowDetailPage'));
const CompliancePage = lazy(() => import('modules/fleet/pages/CompliancePage'));
const GovernancePage = lazy(() => import('modules/fleet/pages/GovernancePage'));
const IntegrationHealthPage = lazy(() => import('modules/fleet/pages/IntegrationHealthPage'));

const FuelDashboardPage = lazy(() => import('modules/fuel/pages/FuelDashboardPage'));
const FuelTransactionsPage = lazy(() => import('modules/fuel/pages/FuelTransactionsPage'));
const FuelTransactionDetailPage = lazy(
  () => import('modules/fuel/pages/FuelTransactionDetailPage'),
);
const DriverLogbooksPage = lazy(() => import('modules/fuel/pages/DriverLogbooksPage'));
const DriverLogbookDetailPage = lazy(() => import('modules/fuel/pages/DriverLogbookDetailPage'));
const FuelReconciliationPage = lazy(() => import('modules/fuel/pages/FuelReconciliationPage'));
const FuelAnomaliesPage = lazy(() => import('modules/fuel/pages/FuelAnomaliesPage'));
const FuelAnomalyDetailPage = lazy(() => import('modules/fuel/pages/FuelAnomalyDetailPage'));
const FuelImportsPage = lazy(() => import('modules/fuel/pages/FuelImportsPage'));
const FuelPoliciesPage = lazy(() => import('modules/fuel/pages/FuelPoliciesPage'));
const FuelPolicyDetailPage = lazy(() => import('modules/fuel/pages/FuelPolicyDetailPage'));
const FuelIntegrationPage = lazy(() => import('modules/fuel/pages/FuelIntegrationPage'));

const DispatchDashboardPage = lazy(() => import('modules/dispatch/pages/DispatchDashboardPage'));
const CourierItemsPage = lazy(() => import('modules/dispatch/pages/CourierItemsPage'));
const CourierItemDetailPage = lazy(() => import('modules/dispatch/pages/CourierItemDetailPage'));
const ManifestsPage = lazy(() => import('modules/dispatch/pages/ManifestsPage'));
const ManifestDetailPage = lazy(() => import('modules/dispatch/pages/ManifestDetailPage'));
const InboundMailPage = lazy(() => import('modules/dispatch/pages/InboundMailPage'));
const DispatchExceptionsPage = lazy(() => import('modules/dispatch/pages/DispatchExceptionsPage'));
const DispatchExceptionDetailPage = lazy(
  () => import('modules/dispatch/pages/DispatchExceptionDetailPage'),
);
const ScanImportsPage = lazy(() => import('modules/dispatch/pages/ScanImportsPage'));
const DispatchIntegrationPage = lazy(
  () => import('modules/dispatch/pages/DispatchIntegrationPage'),
);

const EmergencyDashboardPage = lazy(
  () => import('modules/emergency/pages/EmergencyDashboardPage'),
);
const ActivationsPage = lazy(() => import('modules/emergency/pages/ActivationsPage'));
const ActivationDetailPage = lazy(() => import('modules/emergency/pages/ActivationDetailPage'));
const BreakGlassPage = lazy(() => import('modules/emergency/pages/BreakGlassPage'));
const EmergencyTemplatesPage = lazy(
  () => import('modules/emergency/pages/EmergencyTemplatesPage'),
);
const EmergencyTemplateDetailPage = lazy(
  () => import('modules/emergency/pages/EmergencyTemplateDetailPage'),
);
const EmergencyAudiencesPage = lazy(
  () => import('modules/emergency/pages/EmergencyAudiencesPage'),
);
const EmergencyDrillsPage = lazy(() => import('modules/emergency/pages/EmergencyDrillsPage'));
const EmergencyIntegrationPage = lazy(
  () => import('modules/emergency/pages/EmergencyIntegrationPage'),
);

/**
 * The router basename comes from Vite's `BASE_URL`, which is set by `base` in `vite.config.ts`.
 * Keeping it derived means the mount point is stated once: move the bundle and the routes follow.
 */
const basename = import.meta.env.BASE_URL.replace(/\/$/, '');

const PageFallback = () => (
  <div className="flex min-h-[60vh] items-center justify-center">
    <Spinner size={30} />
  </div>
);

/**
 * A system's routes, refused when the actor is not entitled to it.
 *
 * The wrapper sits on the parent route so every child inherits it — there is no way to add a screen
 * under `fleet` or `emergency` and forget the check.
 *
 * It takes the **system**, not the programme, because the system is the more specific fact and the
 * programme follows from it. Passing both would let a route claim `dispatch` belongs to SSEMP; the
 * model owns that mapping instead. It is a usability control, not the enforcement point: the services
 * authorise every call regardless. See `RequireEntitlement` and ADR 0005.
 */
const SystemRoutes = ({ system }: { system: SystemCode }) => (
  <RequireEntitlement system={system}>
    <Outlet />
  </RequireEntitlement>
);

const App = () => {
  // Where this actor lands, which is their first entitled destination rather than the fleet
  // dashboard — that is only the right answer for a fleet user.
  const home = landingPath();

  return (
  <BrowserRouter basename={basename}>
    <ScrollToTop />
    <NotifierProvider>
      <Suspense fallback={<PageFallback />}>
        <Routes>
          <Route element={<AppShell />}>
            <Route
              index
              element={home ? <Navigate to={home} replace /> : <NoProgrammePage />}
            />
            <Route path="facilities" element={<SystemRoutes system="S152" />}>
              <Route index element={<FacilitiesDashboardPage />} />
              <Route path="sites">
                <Route index element={<SiteRegisterPage />} />
                <Route path=":siteId" element={<SiteDetailPage />} />
              </Route>
              <Route path="spaces">
                <Route index element={<SpaceRegisterPage />} />
                <Route path=":roomId" element={<SpaceDetailPage />} />
              </Route>
              <Route path="assets">
                <Route index element={<AssetRegisterPage />} />
                <Route path=":assetId" element={<AssetDetailPage />} />
              </Route>
              <Route path="zones" element={<ZonesPage />} />
              <Route path="devices" element={<DeviceReferencesPage />} />
              <Route path="assessments">
                <Route index element={<ReadinessAssessmentsPage />} />
                <Route path=":assessmentId" element={<ReadinessAssessmentDetailPage />} />
              </Route>
              <Route path="checklists">
                <Route index element={<ReadinessChecklistsPage />} />
                <Route path=":checklistId" element={<ReadinessChecklistDetailPage />} />
              </Route>
              <Route path="audit" element={<FacilitiesAuditPage />} />
              <Route path="configuration" element={<FacilitiesConfigurationPage />} />
            </Route>
            {/*
              S153 shares the /facilities base with S152 — same service, same programme — but is
              guarded on its own system code, so a role entitled to one and not the other lands on
              the no-entitlement page rather than an empty screen. Evidence sits outside the
              /maintenance prefix because an auditor reaching a piece of evidence has no interest in
              the planning register it came from.
            */}
            <Route path="facilities" element={<SystemRoutes system="S153" />}>
              <Route path="faults">
                <Route index element={<FaultRegisterPage />} />
                <Route path=":faultId" element={<FaultDetailPage />} />
              </Route>
              <Route path="work-orders">
                <Route index element={<WorkOrderQueuePage />} />
                <Route path=":workOrderId" element={<WorkOrderDetailPage />} />
              </Route>
              <Route path="maintenance/schedules">
                <Route index element={<PreventiveSchedulesPage />} />
                <Route path=":scheduleId" element={<ScheduleDetailPage />} />
              </Route>
              <Route path="maintenance/vendors" element={<MaintenanceVendorsPage />} />
              <Route path="maintenance-evidence/:evidenceId" element={<EvidenceDetailPage />} />
            </Route>
            <Route path="fleet" element={<SystemRoutes system="S166" />}>
              <Route index element={<FleetDashboardPage />} />
              <Route path="vehicles">
                <Route index element={<VehicleRegisterPage />} />
                <Route path=":vehicleId" element={<VehicleDetailPage />} />
              </Route>
              <Route path="drivers">
                <Route index element={<DriverRegisterPage />} />
                <Route path=":driverId" element={<DriverDetailPage />} />
              </Route>
              <Route path="trips">
                <Route index element={<TripQueuePage />} />
                <Route path=":tripId" element={<TripDetailPage />} />
              </Route>
              <Route path="workflow">
                <Route index element={<WorkflowQueuePage />} />
                <Route path=":itemId" element={<WorkflowDetailPage />} />
              </Route>
              <Route path="compliance" element={<CompliancePage />} />
              <Route path="governance" element={<GovernancePage />} />
              <Route path="integrations" element={<IntegrationHealthPage />} />
            </Route>
            <Route path="fuel" element={<SystemRoutes system="S168" />}>
              <Route index element={<FuelDashboardPage />} />
              <Route path="transactions">
                <Route index element={<FuelTransactionsPage />} />
                <Route path=":transactionId" element={<FuelTransactionDetailPage />} />
              </Route>
              <Route path="logbooks">
                <Route index element={<DriverLogbooksPage />} />
                <Route path=":logbookId" element={<DriverLogbookDetailPage />} />
              </Route>
              <Route path="reconciliation" element={<FuelReconciliationPage />} />
              <Route path="anomalies">
                <Route index element={<FuelAnomaliesPage />} />
                <Route path=":anomalyId" element={<FuelAnomalyDetailPage />} />
              </Route>
              <Route path="imports" element={<FuelImportsPage />} />
              <Route path="policies">
                <Route index element={<FuelPoliciesPage />} />
                <Route path=":policyId" element={<FuelPolicyDetailPage />} />
              </Route>
              <Route path="integrations" element={<FuelIntegrationPage />} />
            </Route>
            <Route path="dispatch" element={<SystemRoutes system="S171" />}>
              <Route index element={<DispatchDashboardPage />} />
              <Route path="items">
                <Route index element={<CourierItemsPage />} />
                <Route path=":itemId" element={<CourierItemDetailPage />} />
              </Route>
              <Route path="manifests">
                <Route index element={<ManifestsPage />} />
                <Route path=":manifestId" element={<ManifestDetailPage />} />
              </Route>
              <Route path="inbound" element={<InboundMailPage />} />
              <Route path="exceptions">
                <Route index element={<DispatchExceptionsPage />} />
                <Route path=":caseId" element={<DispatchExceptionDetailPage />} />
              </Route>
              <Route path="scans" element={<ScanImportsPage />} />
              <Route path="integrations" element={<DispatchIntegrationPage />} />
            </Route>
            <Route path="emergency" element={<SystemRoutes system="S174" />}>
              <Route index element={<EmergencyDashboardPage />} />
              <Route path="activations">
                <Route index element={<ActivationsPage />} />
                <Route path=":activationId" element={<ActivationDetailPage />} />
              </Route>
              <Route path="break-glass" element={<BreakGlassPage />} />
              <Route path="templates">
                <Route index element={<EmergencyTemplatesPage />} />
                <Route path=":templateId" element={<EmergencyTemplateDetailPage />} />
              </Route>
              <Route path="audiences" element={<EmergencyAudiencesPage />} />
              <Route path="drills" element={<EmergencyDrillsPage />} />
              <Route path="integrations" element={<EmergencyIntegrationPage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </NotifierProvider>
  </BrowserRouter>
  );
};

export default App;
