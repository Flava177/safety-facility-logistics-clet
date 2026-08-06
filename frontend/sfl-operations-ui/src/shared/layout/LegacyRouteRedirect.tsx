import { Navigate, useLocation } from 'react-router';

/**
 * Keeps the pre-platform URLs working.
 *
 * <h2>Why these exist at all</h2>
 *
 * <p>The dashboard's routes were named after *systems* - `/fleet`, `/fuel`, `/dispatch`,
 * `/emergency`, `/bookings` - and are now named after the *platforms* that deploy them. That is the
 * right shape, and it invalidates every link anyone has already saved: a bookmarked trip register, a
 * URL pasted into a ticket, a screenshot in a runbook, the browser history of everybody who has used
 * the dashboard this week.
 *
 * <p>None of those are worth breaking for a rename. A 404 on a bookmark is indistinguishable from a
 * broken deployment to the person holding it, and it is the kind of breakage that gets reported as
 * "the dashboard is down".
 *
 * <h2>What they do</h2>
 *
 * <p>Swap the leading segment and keep everything after it, including the query string and hash - so
 * `/fleet/trips/abc-123?status=PLANNED` reaches the same trip with the same filter applied, rather
 * than dumping the reader on a register and making them find it again.
 *
 * <p>`replace` rather than a push: the old URL should not sit in the back stack, or Back from the new
 * page bounces through the redirect and forward again.
 *
 * <h2>They are meant to be removed</h2>
 *
 * <p>This is a compatibility shim with a lifetime, not a second naming scheme to maintain. Once the
 * saved links have aged out - one release is the usual measure - delete the block in `App.tsx` and
 * this file with it. Leaving them forever means every future reader has to work out which of two
 * spellings is real.
 */

interface LegacyRouteRedirectProps {
  /** The old leading path, without a trailing slash - for example `/emergency`. */
  from: string;
  /** What it becomes - for example `/safetysecurity`. */
  to: string;
}

const LegacyRouteRedirect = ({ from, to }: LegacyRouteRedirectProps) => {
  const location = useLocation();

  /*
    Only the prefix is swapped, and only where it actually is a prefix. `startsWith` guards against a
    path that merely contains the old name - a future `/reports/fleet` must not be rewritten to
    `/reports/fleetvehicle/fleet`, which a bare `replace()` would happily do.
  */
  const rest = location.pathname.startsWith(from) ? location.pathname.slice(from.length) : '';
  const target = `${to}${rest}${location.search}${location.hash}`;

  return <Navigate to={target} replace />;
};

export default LegacyRouteRedirect;

/**
 * Every route that moved, oldest spelling first.
 *
 * <p>Declared here rather than inline in the router so the list is readable as a list - it is the
 * answer to "where did that page go", and somebody will need that answer without reading JSX.
 */
export const LEGACY_ROUTES: LegacyRouteRedirectProps[] = [
  // --- system-named top levels, before platforms owned them ------------------------------------
  // FTLMP: three systems that now share their deployable's namespace.
  { from: '/fleet', to: '/fleetvehicle/fleet' },
  { from: '/fuel', to: '/fleetvehicle/fuel' },
  { from: '/dispatch', to: '/fleetvehicle/dispatch' },
  { from: '/login', to: '/fleetvehicle/login' },
  // SSEMP: the module was named for the one system it had; the platform has four more coming.
  { from: '/emergency', to: '/safetysecurity/emergency' },
  // IFIMP: booking was the odd one out, top-level while its sibling S152 screens were not.
  { from: '/bookings', to: '/facilities/bookings' },

  /*
    --- the flat platform level, before each system owned a segment ------------------------------

    Listed one resource at a time rather than as a prefix, and that is not verbosity. A prefix rule
    from `/facilities` would also match `/facilities/estate/sites` - the destination - and rewrite it
    again on arrival, which is an infinite redirect rather than a compatibility shim. Naming the
    resources keeps every rule terminal.
  */
  // IFIMP S152 -> /facilities/estate
  { from: '/facilities/sites', to: '/facilities/estate/sites' },
  { from: '/facilities/buildings', to: '/facilities/estate/buildings' },
  { from: '/facilities/spaces', to: '/facilities/estate/spaces' },
  { from: '/facilities/assets', to: '/facilities/estate/assets' },
  { from: '/facilities/zones', to: '/facilities/estate/zones' },
  { from: '/facilities/devices', to: '/facilities/estate/devices' },
  { from: '/facilities/assessments', to: '/facilities/estate/assessments' },
  { from: '/facilities/checklists', to: '/facilities/estate/checklists' },
  { from: '/facilities/audit', to: '/facilities/estate/audit' },
  { from: '/facilities/configuration', to: '/facilities/estate/configuration' },
  // IFIMP S153 -> /facilities/maintenance
  { from: '/facilities/faults', to: '/facilities/maintenance/faults' },
  { from: '/facilities/work-orders', to: '/facilities/maintenance/work-orders' },
  { from: '/facilities/maintenance-evidence', to: '/facilities/maintenance/evidence' },
  // SSEMP S174 -> /safetysecurity/emergency
  { from: '/safetysecurity/activations', to: '/safetysecurity/emergency/activations' },
  { from: '/safetysecurity/break-glass', to: '/safetysecurity/emergency/break-glass' },
  { from: '/safetysecurity/templates', to: '/safetysecurity/emergency/templates' },
  { from: '/safetysecurity/audiences', to: '/safetysecurity/emergency/audiences' },
  { from: '/safetysecurity/drills', to: '/safetysecurity/emergency/drills' },
  { from: '/safetysecurity/integrations', to: '/safetysecurity/emergency/integrations' },
];
