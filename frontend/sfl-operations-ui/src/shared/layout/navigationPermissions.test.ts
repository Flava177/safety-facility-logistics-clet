import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * What each role is offered, pinned against the permission sets the services actually enforce.
 *
 * <h2>The defect this exists to stop coming back</h2>
 *
 * Most FTLMP nav items declared **no permission at all**, so entitlement to S166 showed every one of
 * them. A driver — who holds four fleet permissions and four fuel ones — was offered the workflow
 * queue, the driver register, compliance, evidence and audit, reconciliation, anomaly cases, CSV
 * imports, fuel policies and provider integration. Nine screens, every one of which answers
 * `FLEET_UNAUTHORIZED_SCOPE` or its fuel equivalent on arrival.
 *
 * That is not a cosmetic problem. A menu that offers what the service refuses teaches an operator
 * that the application is unreliable, and it buries the items they *can* use among ones they cannot.
 *
 * <h2>Why the permission sets are restated here</h2>
 *
 * `permits` is backed by a live call to each service's `/actor/permissions`, which cannot run in a
 * unit test. The sets below are transcribed from `FleetPermissionMatrix` and `FuelPermissionMatrix`
 * — if either changes and this file does not, these tests keep asserting the old contract, which is
 * the honest failure mode: it fails loudly rather than silently agreeing with whatever the code now
 * does.
 */

const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const entitledSystems = vi.hoisted(() => ({ value: [] as string[] }));
vi.mock('shared/layout/programmes', async () => {
  const model = await vi.importActual<typeof import('./programmeModel')>('./programmeModel');
  return {
    ...model,
    entitledTo: () => true,
    entitledToSystem: (code: string) => entitledSystems.value.includes(code),
    portalLabel: () => 'test',
  };
});

vi.mock('shared/layout/personas', () => ({
  isPersona: () => false,
  PersonaCode: {},
}));

const { entitledSections } = await import('./navigation');

/**
 * FLEET_DRIVER, transcribed from `FleetPermissionMatrix` and `FuelPermissionMatrix`.
 *
 * Transcribed, therefore capable of drifting — and it did: `FLEET_DRIVER_READ` and
 * `FLEET_TRIP_ACKNOWLEDGE` were granted in the Java matrix and this list went on asserting the
 * driver's menu as if they had not been, so the suite stayed green while describing a role that no
 * longer existed. There is no way to read the Java enum from vitest; the mitigation is that this
 * list names its source in one place and the assertions below quote the reason for each entry, so a
 * mismatch is visible to whoever changes the matrix.
 */
const DRIVER = [
  'FLEET_VEHICLE_READ',
  'FLEET_DRIVER_READ',
  'FLEET_TRIP_READ',
  'FLEET_TRIP_ACKNOWLEDGE',
  'FLEET_INSPECTION_RECORD',
  'FLEET_EVIDENCE_REGISTER',
  'FUEL_TRANSACTION_READ',
  'FUEL_LOGBOOK_READ',
  'FUEL_LOGBOOK_CREATE',
  'FUEL_LOGBOOK_SUBMIT',
];

const holding = (granted: string[]) =>
  permits.mockImplementation((p) => p === undefined || granted.includes(p));

const labelsFor = (systems: string[]): string[] => {
  entitledSystems.value = systems;
  return entitledSections().flatMap((section) => section.items.map((item) => item.label));
};

beforeEach(() => {
  permits.mockReset();
  entitledSystems.value = [];
});

describe('what a driver is offered', () => {
  it('does not offer the workflow queue', () => {
    // The supervisor's view of inspections, defects and escalations across the whole fleet. A
    // driver records an inspection against their own trip and has no business in the queue.
    holding(DRIVER);
    expect(labelsFor(['S166', 'S168'])).not.toContain('Workflow queue');
  });

  it('offers the driver register, which they may read and not write', () => {
    /*
      Read-only, and the read is the point: a driver looks up the colleague covering their trip. The
      write side is FLEET_DRIVER_MANAGE, which is absent from DRIVER above — so no registering,
      editing or retiring, including of themselves — and licence numbers arrive masked because
      FLEET_DRIVER_SENSITIVE_READ is absent too. Navigation grants sight of the page; it grants
      nothing on it.
    */
    holding(DRIVER);
    expect(labelsFor(['S166', 'S168'])).toContain('Driver register');
  });

  it('does not offer any assurance screen', () => {
    /*
      A driver holds FLEET_EVIDENCE_REGISTER — they attach evidence to their own trip closure — and
      that is deliberately not a licence to read the fleet's evidence library or replay the audit
      chain, which is FLEET_EVIDENCE_READ.
    */
    holding(DRIVER);
    const labels = labelsFor(['S166', 'S168']);
    expect(labels).not.toContain('Compliance & service');
    expect(labels).not.toContain('Evidence & audit');
    expect(labels).not.toContain('Integration health');
  });

  it('does not offer fuel policies or provider integration', () => {
    // The limits every reconciliation judges a driver against. Being judged by them is not a reason
    // to read, still less edit, the thresholds.
    holding(DRIVER);
    const labels = labelsFor(['S166', 'S168']);
    expect(labels).not.toContain('Fuel policies');
    expect(labels).not.toContain('Provider integration');
    expect(labels).not.toContain('Fuel cards');
    expect(labels).not.toContain('Reconciliation');
    expect(labels).not.toContain('Anomaly cases');
    expect(labels).not.toContain('CSV imports');
  });

  it('does not offer either dashboard', () => {
    holding(DRIVER);
    const labels = labelsFor(['S166', 'S168']);
    expect(labels).not.toContain('Dashboard');
    expect(labels).not.toContain('Fuel dashboard');
  });

  it('offers exactly the five screens a driver can actually use', () => {
    /*
      The positive assertion, and the one that would catch an over-correction — a tightening pass is
      as capable of leaving a role unable to work as it is of leaving it able to see too much.

      Each is here for a stated reason:
        - Trips: their own, narrowed server-side to the driver bound to their sign-in, and the screen
          where they confirm or defer.
        - Vehicle register, Driver register: read, no write. They look up the vehicle they are taking
          and the colleague covering for them.
        - Driver logbooks: narrowed per record on created_by.
        - Fuel transactions: narrowed per record on driver_id. This line previously carried a note
          calling that a "known gap … the service does not narrow transactions per driver" — it did
          not, and now does, in FuelApplicationService.transactions.
    */
    holding(DRIVER);
    expect(labelsFor(['S166', 'S168']).sort()).toEqual(
      [
        'Driver logbooks',
        'Driver register',
        'Fuel transactions',
        'Trips & assignments',
        'Vehicle register',
      ].sort(),
    );
  });
});

describe('what a fleet manager is offered', () => {
  it('still sees the operational screens a driver does not', () => {
    // The other half of the correction: tightening the driver must not narrow the role the module
    // was built for.
    holding([
      ...DRIVER,
      'FLEET_DASHBOARD_READ',
      'FLEET_WORKFLOW_READ',
      'FLEET_DRIVER_READ',
      'FLEET_COMPLIANCE_MANAGE',
      'FLEET_EVIDENCE_READ',
      'FUEL_REPORT_READ',
      'FUEL_CARD_READ',
      'FUEL_POLICY_READ',
      'FUEL_ANOMALY_READ',
    ]);
    const labels = labelsFor(['S166', 'S168']);
    expect(labels).toContain('Dashboard');
    expect(labels).toContain('Workflow queue');
    expect(labels).toContain('Driver register');
    expect(labels).toContain('Compliance & service');
    expect(labels).toContain('Fuel cards');
    expect(labels).toContain('Fuel policies');
  });
});

describe('the fail-open contract', () => {
  it('offers everything when the services could not be asked', () => {
    /*
      `permits` returns true for everything when no service answered, and that is deliberate: a
      dashboard that hides screens because a request failed reads as a broken build, and the
      services refuse anything the actor cannot do regardless. Gating more items makes this contract
      matter more, not less, so it is pinned here.
    */
    permits.mockReturnValue(true);
    const labels = labelsFor(['S166', 'S168']);
    expect(labels).toContain('Workflow queue');
    expect(labels).toContain('Fuel policies');
  });
});
