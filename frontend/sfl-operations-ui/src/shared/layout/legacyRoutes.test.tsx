import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import LegacyRouteRedirect, { LEGACY_ROUTES } from './LegacyRouteRedirect';
import {
  authPaths,
  facilitiesPaths,
  bookingPaths,
  dispatchPaths,
  emergencyPaths,
  fleetPaths,
  fuelPaths,
} from './navigation';

/**
 * The pre-platform URLs still arrive somewhere useful.
 *
 * <p>A redirect shim is the kind of code that rots without anybody noticing: it is never exercised by
 * the people who wrote it, because they already know the new addresses. The failure only shows up as
 * a colleague's bookmark returning a blank screen, weeks later, reported as "the dashboard is down".
 *
 * <p>So this asserts the two things worth asserting - that every old prefix reaches the matching new
 * one, and that the rest of the URL survives the hop. The second is the part that makes a redirect
 * worth having at all: landing on a register when the link named a record is barely better than a
 * 404.
 */

const Probe = () => {
  const location = useLocation();
  return <span data-testid="landed">{location.pathname + location.search + location.hash}</span>;
};

/*
  Unmounts before returning. Each call renders into the same document, so without this the second
  assertion in a test finds two probes and fails on the helper rather than on the redirect.
*/
const landOn = (start: string): string => {
  const view = render(
    <MemoryRouter initialEntries={[start]}>
      <Routes>
        {LEGACY_ROUTES.map((route) => (
          <Route
            key={route.from}
            path={`${route.from.slice(1)}/*`}
            element={<LegacyRouteRedirect {...route} />}
          />
        ))}
        {LEGACY_ROUTES.map((route) => (
          <Route
            key={`${route.from}-exact`}
            path={route.from.slice(1)}
            element={<LegacyRouteRedirect {...route} />}
          />
        ))}
        <Route path="*" element={<Probe />} />
      </Routes>
    </MemoryRouter>,
  );
  const landed = screen.getByTestId('landed').textContent ?? '';
  view.unmount();
  return landed;
};

describe('legacy dashboard routes', () => {
  it('sends each old top-level path to its platform', () => {
    expect(landOn('/fleet')).toBe(fleetPaths.dashboard);
    expect(landOn('/fuel')).toBe(fuelPaths.dashboard);
    expect(landOn('/dispatch')).toBe(dispatchPaths.dashboard);
    expect(landOn('/emergency')).toBe(emergencyPaths.dashboard);
    expect(landOn('/bookings')).toBe(bookingPaths.diary);
    expect(landOn('/login')).toBe(authPaths.login);
  });

  it('keeps the rest of the path, so a link to a record still reaches that record', () => {
    expect(landOn('/fleet/trips/abc-123')).toBe(fleetPaths.tripDetail('abc-123'));
    expect(landOn('/fuel/transactions/tx-9')).toBe(fuelPaths.transactionDetail('tx-9'));
    expect(landOn('/dispatch/manifests/m-1')).toBe(dispatchPaths.manifestDetail('m-1'));
    // Emergency loses a level rather than gaining one - its children sit directly under the
    // platform, so this is the case a naive "add a prefix" rewrite would get wrong.
    expect(landOn('/emergency/drills')).toBe(emergencyPaths.drills);
    expect(landOn('/emergency/activations/act-1')).toBe(emergencyPaths.activationDetail('act-1'));
  });

  it('keeps the query string and the hash', () => {
    expect(landOn('/fleet/trips?status=PLANNED')).toBe(`${fleetPaths.trips}?status=PLANNED`);
    expect(landOn('/fuel/anomalies?severity=HIGH#top')).toBe(`${fuelPaths.anomalies}?severity=HIGH#top`);
  });

  it('adds the module level for the systems that gained one', () => {
    expect(landOn('/facilities/sites')).toBe(facilitiesPaths.sites);
    expect(landOn('/facilities/faults/f-1')).toBe(facilitiesPaths.faultDetail('f-1'));
    expect(landOn('/facilities/work-orders')).toBe(facilitiesPaths.workOrders);
    expect(landOn('/safetysecurity/drills')).toBe(emergencyPaths.drills);
    expect(landOn('/safetysecurity/activations/a-1')).toBe(emergencyPaths.activationDetail('a-1'));
  });

  it('never redirects a destination, so no rule can loop', () => {
    /*
      The failure this guards is the one a prefix rule invites: a rule from `/facilities` would also
      match `/facilities/estate/sites`, its own destination, and rewrite it again forever. Asserting
      that no target is itself matched by any rule keeps every entry terminal, whatever gets added
      later.
    */
    for (const route of LEGACY_ROUTES) {
      const loops = LEGACY_ROUTES.filter(
        (other) => route.to === other.from || route.to.startsWith(`${other.from}/`),
      );
      expect(loops, `${route.from} -> ${route.to} is rewritten again by ${loops[0]?.from}`).toEqual([]);
    }
  });

  it('sends every entry to one of the three platforms', () => {
    const platforms = ['/fleetvehicle/', '/facilities/', '/safetysecurity/'];
    for (const route of LEGACY_ROUTES) {
      expect(platforms.some((p) => route.to.startsWith(p)), route.to).toBe(true);
    }
  });
});
