import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { authenticationRequired } from 'shared/api/config';
import { isSignedIn } from './session';

/**
 * Sends an unauthenticated visitor to the sign-in page.
 *
 * <h2>Why this is conditional rather than absolute</h2>
 *
 * It refuses entry only when {@link authenticationRequired} is true, and that flag defaults to
 * **false** — which looks like the wrong default until you see what the services do.
 *
 * A service running with `SFL_SECURITY_ENABLED=false` accepts the `X-SFL-*` headers and issues no
 * tokens; there is no Keycloak in that setup and nothing to sign in against. Forcing the login page
 * there would produce a form that cannot succeed — the realm is not running, so every attempt fails
 * with "identity provider unreachable" and the dashboard becomes unreachable with it. That is
 * exactly the shape of failure this codebase keeps finding: a control that cannot be satisfied.
 *
 * So the rule is: **the dashboard demands a session when it has been told the services demand one.**
 * `VITE_SFL_AUTH_REQUIRED=true` is set alongside a service running with security on, and the two
 * move together. `start-fleet.ps1` sets neither, which is why local header-based work is unchanged.
 *
 * This is a usability control and never the enforcement point. Bypassing it buys nothing: without a
 * token the services answer 401 to every call, and the screens behind it would be empty.
 */
const RequireSession = ({ children }: { children: ReactNode }) => {
  const location = useLocation();

  if (!authenticationRequired || isSignedIn()) {
    return <>{children}</>;
  }

  // `replace` so the back button does not bounce between a guarded route and the form, and the
  // attempted path travels along so sign-in can return there rather than dumping everyone on the
  // landing page.
  return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
};

export default RequireSession;
