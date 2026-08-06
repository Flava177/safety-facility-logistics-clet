import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * What each role is offered, at the level of individual controls.
 *
 * <h2>The defect this exists to stop coming back</h2>
 *
 * <p>Every helper in `access.ts` was written, documented, and then wired to almost nothing. Of about
 * thirty, two were called. So a reporting viewer - who holds four fuel permissions, all of them
 * reads - was shown **Capture transaction**, **Export CSV**, **Create logbook**, **Import a CSV**,
 * **Create policy**, **Reconcile** and **Void**, and every one of them refused on the way to the
 * service. The navigation gating was correct throughout, which is why it looked fine.
 *
 * <p>Asserting the helpers rather than each page is deliberate: the helpers are the contract, there
 * are far fewer of them than call sites, and a page that forgets to call one is caught by its own
 * test. What this pins is that the *answers* are right for the roles that matter.
 */

const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const access = await import('./access');

/** The fuel permissions FLEET_REPORTING_VIEWER actually holds, from FuelPermissionMatrix. */
const REPORTING_VIEWER = [
  'FUEL_TRANSACTION_READ',
  'FUEL_LOGBOOK_READ',
  'FUEL_ANOMALY_READ',
  'FUEL_REPORT_READ',
  // Fleet side: the read-only set plus dashboard drilldown.
  'FLEET_VEHICLE_READ',
  'FLEET_DRIVER_READ',
  'FLEET_TRIP_READ',
  'FLEET_DASHBOARD_READ',
  'FLEET_DASHBOARD_DRILLDOWN',
];

/** What an auditor holds that a reporting viewer does not. */
const AUDITOR = [...REPORTING_VIEWER, 'FLEET_AUDIT_READ', 'FLEET_AUDIT_INTEGRITY_CHECK', 'FUEL_REPORT_EXPORT'];

const holding = (granted: string[]) =>
  permits.mockImplementation((p) => p === undefined || granted.includes(p));

describe('a reporting viewer', () => {
  beforeEach(() => {
    permits.mockReset();
    holding(REPORTING_VIEWER);
  });

  it('is offered no fuel control that writes', () => {
    expect(access.canCaptureFuel()).toBe(false);
    expect(access.canVoidFuel()).toBe(false);
    expect(access.canImportFuel()).toBe(false);
    expect(access.canManageFuelPolicies()).toBe(false);
    expect(access.canRunReconciliation()).toBe(false);
    expect(access.canCreateLogbooks()).toBe(false);
    expect(access.canReviewLogbooks()).toBe(false);
    expect(access.canManageAnomalies()).toBe(false);
    expect(access.canManageFuelCards()).toBe(false);
  });

  it('cannot export the fuel report, which is not the same as reading it', () => {
    // The distinction the matrix has always drawn: figures on screen versus a CSV of every fill at
    // a site walking out of the building.
    expect(access.canExportFuelReports()).toBe(false);
  });

  it('is offered no fleet register control that writes', () => {
    expect(access.canManageVehicles()).toBe(false);
    expect(access.canManageDrivers()).toBe(false);
    expect(access.canManageTrips()).toBe(false);
    expect(access.canRecordInspections()).toBe(false);
    expect(access.canManageCompliance()).toBe(false);
  });

  it('cannot reach the audit trail or replay the hash chain', () => {
    // Reading the audit trail is an auditor's grant, and replaying the chain narrower still - a
    // fleet manager does not audit their own service.
    expect(access.canReadAudit()).toBe(false);
    expect(access.canVerifyAuditChain()).toBe(false);
  });
});

describe('an auditor', () => {
  beforeEach(() => {
    permits.mockReset();
    holding(AUDITOR);
  });

  it('may replay the chain and export, and still writes nothing', () => {
    expect(access.canReadAudit()).toBe(true);
    expect(access.canVerifyAuditChain()).toBe(true);
    expect(access.canExportFuelReports()).toBe(true);
    expect(access.canCaptureFuel()).toBe(false);
    expect(access.canManageVehicles()).toBe(false);
  });
});

describe('when the services could not be asked', () => {
  it('offers everything rather than hiding the application', () => {
    // `permits` fails open on a null permission set, and these helpers must not second-guess it: a
    // dashboard that hides half its controls because a request timed out reads as a broken build,
    // and the services refuse anything the actor cannot do regardless.
    permits.mockReset();
    permits.mockReturnValue(true);
    expect(access.canCaptureFuel()).toBe(true);
    expect(access.canVerifyAuditChain()).toBe(true);
  });
});
