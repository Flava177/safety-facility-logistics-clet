import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import logo from 'assets/sfl-logo.png';
import { fleetApiBaseUrl, sflActor } from 'shared/api/config';
import { actorOverridden, devToolsEnabled } from 'shared/dev/actorOverride';
import Button from 'shared/components/Button';
import Icon from 'shared/components/Icon';
import { cn } from 'shared/components/cn';
import { directorate } from './navigation';
import { portalLabel } from './programmes';
import { useSidebar } from './SidebarContext';

/**
 * The development actor switcher, loaded on demand — and only in a development build.
 *
 * `import.meta.env.DEV` is written out here rather than the `devToolsEnabled` re-export on purpose.
 * Vite substitutes that expression with a literal `false` before Rollup runs, so the whole ternary
 * folds and the dynamic import disappears with it: no chunk is emitted and the panel never reaches a
 * production bundle.
 *
 * Guarding the *render* is not enough, and the first attempt at this proved it. `lazy(() => import(…))`
 * at module scope is a real edge in the module graph whatever the JSX below does with the result, and
 * the build duly emitted a 4.57 kB `ActorSwitcher` chunk. The guard has to sit on the import.
 */
const ActorSwitcher = import.meta.env.DEV
  ? lazy(() => import('shared/dev/ActorSwitcher'))
  : null;

const initials = (name: string): string =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('') || 'SF';

const sites = sflActor.sites
  .split(',')
  .map((site) => site.trim())
  .filter(Boolean);

const roles = sflActor.roles
  .split(',')
  .map((role) => role.trim())
  .filter(Boolean);

/**
 * The product bar: full width, CLET Navy, above everything including the navigation rail.
 *
 * It carries identity rather than controls. What sits on the right is the actor the dashboard is
 * actually sending — `X-SFL-User`, roles and site scope go out on every request — because "why
 * can't I see that vehicle?" is nearly always a site-scope question, and the answer belongs on
 * screen rather than in a network trace.
 *
 * `.on-dark` switches the global focus ring to gold; teal on navy would not clear 3:1.
 */
const TopBar = () => {
  const { openMobile } = useSidebar();
  const [profileOpen, setProfileOpen] = useState(false);
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!profileOpen) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [profileOpen]);

  return (
    <header className="on-dark fixed inset-x-0 top-0 z-9999 h-16 bg-brand-800">
      <div className="flex h-full items-center justify-between gap-3 px-4 lg:px-5">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={openMobile}
            aria-label="Open navigation"
            className="flex h-10 w-10 items-center justify-center rounded-lg text-white/80 transition-colors hover:bg-white/10 hover:text-white lg:hidden"
          >
            <Icon name="menu" size={20} />
          </button>

          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
            <img src={logo} alt="" className="h-6 w-6 object-contain" aria-hidden="true" />
          </span>

          <p className="min-w-0 truncate text-theme-md font-bold tracking-tight text-white">
            {directorate.shortName}
            {/* Whichever programme the actor is entitled to, not a fixed "Fleet & Logistics" — that
                label was only ever true for a fleet user, and this bundle now carries SSEMP too. */}
            <span className="ml-2 font-normal text-white/60">— {portalLabel()}</span>
          </p>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span
            className="hidden items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-theme-xs font-medium text-white md:inline-flex"
            title="Site scope sent on every request (X-SFL-Sites)"
          >
            <Icon name="map-pin" size={14} />
            {sites.length === 1 ? sites[0] : `${sites.length} sites`}
          </span>

          <div className="relative" ref={profileRef}>
            <button
              type="button"
              onClick={() => setProfileOpen((open) => !open)}
              aria-expanded={profileOpen}
              aria-haspopup="menu"
              aria-label={`Account: ${sflActor.displayName}`}
              className="flex h-10 w-10 items-center justify-center rounded-full bg-gold-700 text-theme-xs font-bold text-brand-900 transition-colors hover:bg-gold-600"
            >
              {initials(sflActor.displayName)}
            </button>

            {/* An override that looked like the default would eventually be mistaken for one. */}
            {devToolsEnabled && actorOverridden && (
              <span
                aria-hidden="true"
                title="A development actor override is in force"
                className="pointer-events-none absolute -top-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-brand-800 bg-warning-500"
              />
            )}

            {profileOpen && (
              <div
                role="menu"
                className="absolute right-0 z-99999 mt-2 w-80 rounded-lg border border-gray-200 bg-white p-4 text-left shadow-theme-lg"
              >
                <p className="text-theme-sm font-semibold text-gray-900">{sflActor.displayName}</p>
                <p className="text-theme-xs text-gray-600">{sflActor.user}</p>

                <dl className="mt-3 space-y-3 border-t border-gray-200 pt-3">
                  <div>
                    <dt className="text-theme-xs font-semibold tracking-wide text-gray-500 uppercase">
                      Roles
                    </dt>
                    <dd className="mt-1 flex flex-wrap gap-1">
                      {roles.map((role) => (
                        <span
                          key={role}
                          className="rounded-full bg-gray-100 px-2 py-0.5 text-theme-xs font-medium text-gray-700"
                        >
                          {role.replace(/_/g, ' ').toLowerCase()}
                        </span>
                      ))}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-theme-xs font-semibold tracking-wide text-gray-500 uppercase">
                      Site scope
                    </dt>
                    <dd className="mt-1 flex flex-wrap gap-1">
                      {sites.map((site) => (
                        <span
                          key={site}
                          className="rounded-full bg-teal-50 px-2 py-0.5 text-theme-xs font-medium text-teal-800"
                        >
                          {site}
                        </span>
                      ))}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-theme-xs font-semibold tracking-wide text-gray-500 uppercase">
                      Fleet service
                    </dt>
                    <dd className="mt-0.5 text-theme-xs break-all text-gray-700">
                      {fleetApiBaseUrl || 'same origin'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-theme-xs font-semibold tracking-wide text-gray-500 uppercase">
                      Dashboard build
                    </dt>
                    <dd className="mt-0.5 text-theme-xs text-gray-700">
                      {new Date(__BUILD_STAMP__).toLocaleString()}
                    </dd>
                  </div>
                </dl>

                <p className="mt-3 border-t border-gray-200 pt-3 text-theme-xs leading-relaxed text-gray-600">
                  Sign-in is disabled in this environment. These values are sent as
                  <code className="mx-1 rounded bg-gray-100 px-1">X-SFL-*</code>
                  headers on every request.
                </p>

                {devToolsEnabled && (
                  <Button
                    size="sm"
                    variant="outline"
                    startIcon="user"
                    className="mt-3 w-full"
                    onClick={() => {
                      setProfileOpen(false);
                      setSwitcherOpen(true);
                    }}
                  >
                    Change actor
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {ActorSwitcher && switcherOpen && (
        // No fallback surface: the chunk is local and the panel is modal, so a spinner behind a
        // backdrop that has not rendered yet would be the only thing on screen.
        <Suspense fallback={null}>
          <ActorSwitcher open onClose={() => setSwitcherOpen(false)} />
        </Suspense>
      )}
    </header>
  );
};

export default TopBar;

export const SidebarToggle = ({ className }: { className?: string }) => {
  const { expanded, toggleExpanded } = useSidebar();
  return (
    <button
      type="button"
      onClick={toggleExpanded}
      aria-label={expanded ? 'Collapse navigation' : 'Expand navigation'}
      title={expanded ? 'Collapse navigation' : 'Expand navigation'}
      className={cn(
        'hidden h-8 w-8 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-800 lg:flex',
        className,
      )}
    >
      <Icon name={expanded ? 'chevrons-left' : 'chevrons-right'} size={16} />
    </button>
  );
};
