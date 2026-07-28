import { Outlet, useLocation } from 'react-router';
import { cn } from 'shared/components/cn';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import { SidebarProvider, useCloseMobileOnNavigate, useSidebar } from './SidebarContext';

const ShellBody = () => {
  const { expanded } = useSidebar();
  const { pathname } = useLocation();
  useCloseMobileOnNavigate(pathname);

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
          <Outlet />
        </main>
      </div>
    </div>
  );
};

/** Product bar + navigation rail + routed content. Every console screen renders inside this. */
const AppShell = () => (
  <SidebarProvider>
    <ShellBody />
  </SidebarProvider>
);

export default AppShell;
