import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { isSignedIn } from './session';

/**
 * Sends an unauthenticated visitor to the sign-in page.
 *
 * <h2>Why there is no flag on this</h2>
 *
 * The first version made it conditional on a build-time variable, defaulting to off, on the grounds
 * that a service running with security off issues no tokens and forcing a login page there produces
 * a form that cannot succeed.
 *
 * That reasoning was right about a Keycloak-backed form and wrong about this one. Signing in here
 * matches an email against the seeded accounts and makes that account the actor — it depends on
 * nothing external, so it always succeeds and the objection disappears. Leaving the flag in place
 * meant the default run showed no login page at all, which was the whole point of building it.
 *
 * <h2>What it is not</h2>
 *
 * Not an enforcement point, and nothing here pretends otherwise. Bypassing it buys nothing: with the
 * services open the actor is whatever the headers claim, and with the services secure they refuse
 * every call without a token. It decides which portal opens, not what the portal may do.
 */
const RequireSession = ({ children }: { children: ReactNode }) => {
  const location = useLocation();

  if (isSignedIn()) {
    return <>{children}</>;
  }

  // `replace` so the back button does not bounce between a guarded route and the form, and the
  // attempted path travels along so a future version can return there rather than to the landing
  // page.
  return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
};

export default RequireSession;
