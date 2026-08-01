import { useMemo } from 'react';
import { NavLink } from 'react-router';
import { sflActor } from 'shared/api/config';
import Icon from 'shared/components/Icon';
import { cn } from 'shared/components/cn';
import { SidebarToggle } from './TopBar';
import { entitledSections } from './navigation';
import { portalLabel } from './programmes';
import { useSidebar } from './SidebarContext';

const initials = (name: string): string =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('') || 'SF';

/**
 * The navigation rail: white, sitting under the product bar.
 *
 * Destinations are grouped under quiet section labels rather than separated by rules, so the items
 * read as several short lists instead of one long one. The active item takes a tinted pill and gold
 * text — enough to find at a glance, not so much that it competes with the work surface. Only built
 * destinations appear; there are no placeholder entries.
 *
 * **Sections are filtered by programme entitlement.** A fleet operator sees fleet, fuel and
 * dispatch; they do not see emergency mass notification, which is SSEMP. A manager or superadmin
 * sees everything. See `programmes.ts` and ADR 0005 — and note that this is a usability control,
 * never the enforcement point: every service authorises every call on its own.
 */
const Sidebar = () => {
  const { expanded, mobileOpen, closeMobile } = useSidebar();

  const sections = useMemo(() => entitledSections(), []);

  return (
    <>
      {mobileOpen && (
        <div
          className="fixed inset-0 top-16 z-99998 bg-brand-950/40 lg:hidden"
          onClick={closeMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          'fixed top-16 bottom-0 left-0 z-99998 flex flex-col border-r border-gray-200 bg-white transition-all duration-200 ease-in-out',
          expanded ? 'w-[260px]' : 'w-[260px] lg:w-[76px]',
          mobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
        )}
      >
        <nav className="custom-scrollbar flex-1 overflow-y-auto px-3 py-4" aria-label="Sections">
          {sections.map((section) => (
            <div key={section.heading} className="mb-6 last:mb-0">
              {/*
                The module groupings, and they now read as groupings.

                They were `text-gray-500` at the same weight as an inactive item, so "Operations" and
                "Trips & assignments" carried equal visual weight and the sidebar read as one long
                list. A heading's job is to be scannable and *not* look pressable — so it takes the
                brand navy, a heavier weight and letter-spacing, none of which any nav item uses.
                Colour is not doing the work alone: the spacing above and the tracking separate them
                for anyone who cannot distinguish the hues.
              */}
              <p
                className={cn(
                  'mb-2 px-3 text-theme-xs font-bold tracking-wider text-brand-600 uppercase',
                  !expanded && 'lg:hidden',
                )}
              >
                {section.heading}
              </p>
              {!expanded && <div className="mx-3 mb-2 hidden border-t border-gray-200 lg:block" />}

              <ul className="space-y-0.5">
                {section.items.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      end={!item.matchPrefix}
                      title={!expanded ? item.label : undefined}
                      className={({ isActive }) =>
                        cn(
                          'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-theme-sm transition-colors',
                          !expanded && 'lg:justify-center lg:px-0',
                          isActive
                            ? 'bg-gold-50 font-semibold text-gold-900'
                            : 'font-medium text-gray-700 hover:bg-gray-100 hover:text-gray-900',
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          <Icon
                            name={item.icon}
                            size={18}
                            className={cn(
                              'shrink-0 transition-colors',
                              isActive
                                ? 'text-gold-800'
                                : 'text-gray-500 group-hover:text-gray-800',
                            )}
                          />
                          <span className={cn('truncate', !expanded && 'lg:hidden')}>
                            {item.label}
                          </span>
                        </>
                      )}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          {sections.length === 0 && (
            <div className={cn('px-3 py-4', !expanded && 'lg:hidden')}>
              <p className="text-theme-sm font-medium text-gray-800">No programme assigned</p>
              <p className="mt-1 text-theme-xs text-gray-600">
                Your roles do not grant access to any SFL programme, so there is nothing to show
                here. Ask for the role that covers the work you need to do.
              </p>
            </div>
          )}
        </nav>

        <div className="shrink-0 border-t border-gray-200 p-3">
          <div className={cn('flex items-center gap-3', !expanded && 'lg:justify-center')}>
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-800 text-theme-xs font-bold text-white">
              {initials(sflActor.displayName)}
            </span>
            <div className={cn('min-w-0 flex-1', !expanded && 'lg:hidden')}>
              <p className="truncate text-theme-sm font-semibold text-gray-900">
                {sflActor.displayName}
              </p>
              <p className="truncate text-theme-xs text-gray-500" title={portalLabel()}>
                {portalLabel()}
              </p>
            </div>
            <SidebarToggle className={cn(!expanded && 'lg:hidden')} />
          </div>
          {!expanded && (
            <div className="mt-2 hidden justify-center lg:flex">
              <SidebarToggle />
            </div>
          )}
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
