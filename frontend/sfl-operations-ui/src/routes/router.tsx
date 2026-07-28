import { Suspense, lazy } from 'react';
import { Navigate, Outlet, RouteObject, createBrowserRouter, useLocation } from 'react-router';
import App from 'App';
import Page404 from 'pages/errors/Page404';
import SflAppShell from 'shared/layout/SflAppShell';
import { fleetPaths } from 'shared/layout/navigation';
import PageLoader from 'components/loading/PageLoader';

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

export const SuspenseOutlet = () => {
  const location = useLocation();

  return (
    <Suspense key={location.pathname} fallback={<PageLoader />}>
      <Outlet />
    </Suspense>
  );
};

/**
 * Routes.
 *
 * `/` lands on the Fleet operations workspace, not a landing page: this console opens on work.
 */
export const routes: RouteObject[] = [
  {
    element: <App />,
    children: [
      {
        path: '/',
        element: (
          <SflAppShell>
            <SuspenseOutlet />
          </SflAppShell>
        ),
        children: [
          { index: true, element: <Navigate to={fleetPaths.dashboard} replace /> },
          { path: fleetPaths.dashboard, element: <FleetDashboardPage /> },
          { path: fleetPaths.vehicles, element: <VehicleRegisterPage /> },
          { path: `${fleetPaths.vehicles}/:vehicleId`, element: <VehicleDetailPage /> },
          { path: fleetPaths.drivers, element: <DriverRegisterPage /> },
          { path: `${fleetPaths.drivers}/:driverId`, element: <DriverDetailPage /> },
          { path: fleetPaths.trips, element: <TripQueuePage /> },
          { path: `${fleetPaths.trips}/:tripId`, element: <TripDetailPage /> },
          { path: fleetPaths.workflow, element: <WorkflowQueuePage /> },
          { path: `${fleetPaths.workflow}/:itemId`, element: <WorkflowDetailPage /> },
          { path: fleetPaths.compliance, element: <CompliancePage /> },
          { path: fleetPaths.governance, element: <GovernancePage /> },
          { path: fleetPaths.integrations, element: <IntegrationHealthPage /> },
        ],
      },
      { path: '/404', element: <Page404 /> },
      { path: '*', element: <Page404 /> },
    ],
  },
];

const router = createBrowserRouter(routes, {
  basename: import.meta.env.MODE === 'production' ? import.meta.env.VITE_BASENAME : '/',
});

export default router;
