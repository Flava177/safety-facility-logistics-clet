import { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { Spinner } from 'shared/components/DataState';
import { NotifierProvider } from 'shared/components/Notifier';
import AppShell from 'shared/layout/AppShell';
import { fleetPaths } from 'shared/layout/navigation';
import NotFoundPage from 'shared/pages/NotFoundPage';
import ScrollToTop from 'shared/layout/ScrollToTop';

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

const App = () => (
  <BrowserRouter basename={basename}>
    <ScrollToTop />
    <NotifierProvider>
      <Suspense fallback={<PageFallback />}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to={fleetPaths.dashboard} replace />} />
            <Route path="fleet">
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
            <Route path="fuel">
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
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </Suspense>
    </NotifierProvider>
  </BrowserRouter>
);

export default App;
