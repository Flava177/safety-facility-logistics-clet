import { Outlet, useLocation } from 'react-router';
import { cn } from 'shared/components/cn';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import { SidebarProvider, useCloseMobileOnNavigate, useSidebar } from './SidebarContext';
import { permissionFailure } from './actorPermissions';

const ShellBody = () => {
  const { expanded } = useSidebar();
  const { pathname } = useLocation();
  useCloseMobileOnNavigate(pathname);
  /*
    Read once per render rather than held in state: it is decided before the first paint and cannot
    change without a reload, so subscribing to it would be machinery for a value that never moves.
  */
  const permissionsUnavailable = permissionFailure();

  return (
    <div className="min-h-screen bg-gray-50">
      {/*
        SC 2.4.1 Bypass Blocks. The rail is eight links before any page content, and a keyboard or
        screen-reader user should not have to walk them on every navigation.
      */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-999999 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2.5 focus:text-theme-sm focus:font-medium focus:text-brand-900"
      >
        Skip to main content
      </a>

      <TopBar />
      <Sidebar />

      <div
        className={cn(
          'pt-16 transition-all duration-200 ease-in-out',
          expanded ? 'lg:pl-[260px]' : 'lg:pl-[76px]',
        )}
      >
        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-[1600px] px-5 py-6 focus:outline-none lg:px-7"
        >
          {/*
            Permissions could not be loaded, so nothing is being offered.

            This exists because the alternative was worse and invisible. The dashboard used to treat
            an unanswered permission lookup as "allow everything", which meant a service being down
            presented as a driver holding the whole fleet office - and one service being up presented
            as every other platform's controls silently vanishing. Neither said anything. Failing
            closed is only defensible if the operator is told, and this is where they are told.
          */}
          {permissionsUnavailable && (
            <div
              role="status"
              className="mb-5 rounded-lg border border-warning-300 bg-warning-50 px-4 py-3"
            >
              <p className="text-theme-sm font-semibold text-warning-800">
                Your permissions could not be loaded
              </p>
              <p className="mt-1 text-theme-sm text-warning-700">{permissionsUnavailable}</p>
            </div>
          )}
          <Outlet />
        </main>
      </div>
    </div>
  );
};

/** Product bar + navigation rail + routed content. Every dashboard screen renders inside this. */
const AppShell = () => (
  <SidebarProvider>
    <ShellBody />
  </SidebarProvider>
);

export default AppShell;
