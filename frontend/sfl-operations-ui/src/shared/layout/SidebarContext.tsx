import {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

interface SidebarContextValue {
  /** Desktop: full-width rail with labels, or a 88px icon rail. */
  expanded: boolean;
  /** Mobile/tablet: the rail is off-canvas until opened. */
  mobileOpen: boolean;
  toggleExpanded: () => void;
  openMobile: () => void;
  closeMobile: () => void;
}

const SidebarContext = createContext<SidebarContextValue | undefined>(undefined);

const STORAGE_KEY = 'sfl.sidebar.expanded';

export const SidebarProvider = ({ children }: PropsWithChildren) => {
  const [expanded, setExpanded] = useState(() => {
    try {
      return window.localStorage.getItem(STORAGE_KEY) !== 'false';
    } catch {
      return true;
    }
  });
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(expanded));
    } catch {
      // A locked-down browser profile is not a reason to break the layout.
    }
  }, [expanded]);

  const value = useMemo<SidebarContextValue>(
    () => ({
      expanded,
      mobileOpen,
      toggleExpanded: () => setExpanded((current) => !current),
      openMobile: () => setMobileOpen(true),
      closeMobile: () => setMobileOpen(false),
    }),
    [expanded, mobileOpen],
  );

  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
};

export const useSidebar = (): SidebarContextValue => {
  const context = useContext(SidebarContext);
  if (!context) {
    throw new Error('useSidebar must be used inside a SidebarProvider');
  }
  return context;
};

/** Closes the mobile rail whenever the route changes, so a tap never leaves it covering the page. */
export const useCloseMobileOnNavigate = (pathname: string) => {
  const { closeMobile } = useSidebar();
  const close = useCallback(() => closeMobile(), [closeMobile]);
  useEffect(() => {
    close();
  }, [pathname, close]);
};
