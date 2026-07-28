import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import { NotifierProvider } from 'shared/components/Notifier';

/**
 * Application root.
 *
 * Deliberately thin: the operations shell lives in `SflAppShell` and every screen is a route. The
 * notifier wraps everything so any screen can raise a global failure without threading a prop.
 */
const App = () => {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <NotifierProvider>
      <Outlet />
    </NotifierProvider>
  );
};

export default App;
