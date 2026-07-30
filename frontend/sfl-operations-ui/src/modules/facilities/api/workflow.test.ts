import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { FacilityAsset, ReadinessBlocker, Space } from './dto';

/**
 * `permits` reads the actor's permissions, which are fetched once at module scope in the real
 * application. Mocked here so each test can state the actor it is describing rather than
 * bootstrapping one.
 */
const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const { assetBlockerSeverity, setReadinessAction, updateSpaceAction, lockAction, unlockAction } =
  await import('./workflow');

const blocker = (severity: ReadinessBlocker['severity'], resolved = false): ReadinessBlocker =>
  ({
    id: `blocker-${severity}-${resolved}`,
    roomId: 'room-1',
    siteCode: 'ACCRA',
    assessmentId: null,
    source: 'CHECKLIST_ITEM',
    sourceReference: null,
    severity,
    description: `${severity} defect`,
    raisedBy: 'assessor',
    raisedAt: '2026-07-30T09:00:00Z',
    resolved,
    resolvedBy: null,
    resolvedAt: null,
    resolutionNotes: null,
  }) as ReadinessBlocker;

const space = (overrides: Partial<Space> = {}): Space =>
  ({
    id: 'room-1',
    roomCode: 'HALL-A',
    name: 'Moot Courtroom A',
    readinessLocked: false,
    readinessLockedBy: null,
    lifecycleStatus: 'ACTIVE',
    ...overrides,
  }) as Space;

const asset = (
  criticality: FacilityAsset['criticality'],
  overrides: Partial<FacilityAsset> = {},
): FacilityAsset => ({ criticality, ...overrides }) as FacilityAsset;

describe('the critical-blocker rule', () => {
  beforeEach(() => permits.mockReturnValue(true));

  it('refuses READY while a critical blocker is open', () => {
    const action = setReadinessAction('READY', [blocker('CRITICAL')]);

    expect(action.allowed).toBe(false);
    expect(action.reason).toBe('1 critical blocker must be resolved first.');
  });

  it('counts the critical blockers in the reason', () => {
    const action = setReadinessAction('READY', [blocker('CRITICAL'), blocker('MAJOR')]);
    expect(action.reason).toBe('1 critical blocker must be resolved first.');
  });

  it('permits READY once the critical blockers are resolved', () => {
    expect(setReadinessAction('READY', [blocker('CRITICAL', true)]).allowed).toBe(true);
  });

  it('permits READY when only lesser blockers are open', () => {
    // Major and minor degrade a space; they do not stop it being declared ready by hand.
    expect(setReadinessAction('READY', [blocker('MAJOR'), blocker('ADVISORY')]).allowed).toBe(true);
  });

  it('permits BLOCKED and DEGRADED whatever is open', () => {
    expect(setReadinessAction('BLOCKED', [blocker('CRITICAL')]).allowed).toBe(true);
    expect(setReadinessAction('DEGRADED', [blocker('CRITICAL')]).allowed).toBe(true);
  });

  it('refuses any readiness change without the assess permission', () => {
    permits.mockReturnValue(false);
    expect(setReadinessAction('DEGRADED', []).allowed).toBe(false);
  });
});

describe('the examination lock', () => {
  beforeEach(() => permits.mockReturnValue(true));

  it('refuses an attribute edit while the space is locked', () => {
    const action = updateSpaceAction(space({ readinessLocked: true, readinessLockedBy: 'supervisor' }));

    expect(action.allowed).toBe(false);
    expect(action.reason).toContain('supervisor');
    expect(action.reason).toContain('Release the lock first');
  });

  it('allows an edit once the lock is released', () => {
    expect(updateSpaceAction(space()).allowed).toBe(true);
  });

  it('refuses an edit to an archived space', () => {
    expect(updateSpaceAction(space({ lifecycleStatus: 'ARCHIVED' })).allowed).toBe(false);
  });

  it('offers only the applicable half of the lock pair', () => {
    expect(lockAction(space()).allowed).toBe(true);
    expect(unlockAction(space()).allowed).toBe(false);

    const locked = space({ readinessLocked: true });
    expect(lockAction(locked).allowed).toBe(false);
    expect(unlockAction(locked).allowed).toBe(true);
  });

  it('refuses either without the override permission', () => {
    permits.mockReturnValue(false);
    expect(lockAction(space()).allowed).toBe(false);
    expect(unlockAction(space({ readinessLocked: true })).allowed).toBe(false);
  });
});

describe('the readiness consequence of an asset change', () => {
  /**
   * Mirrors `ReadinessApplicationService.severityFor`: criticality sets the ceiling, status sets how
   * much of it applies. Asserted because the dialog shows this to an operator *before* they commit,
   * and a preview that disagrees with what the service then does is worse than no preview.
   */
  it('blocks a space when a critical asset goes out of service', () => {
    expect(assetBlockerSeverity(asset('CRITICAL'), 'OUT_OF_SERVICE')).toBe('CRITICAL');
  });

  it('degrades rather than blocks when a critical asset is only impaired', () => {
    expect(assetBlockerSeverity(asset('CRITICAL'), 'DEGRADED')).toBe('MAJOR');
    expect(assetBlockerSeverity(asset('CRITICAL'), 'UNDER_MAINTENANCE')).toBe('MAJOR');
  });

  it('never lets a low-criticality asset rise above advisory', () => {
    // A failed noticeboard light does not stop an examination.
    expect(assetBlockerSeverity(asset('LOW'), 'OUT_OF_SERVICE')).toBe('ADVISORY');
    expect(assetBlockerSeverity(asset('LOW'), 'DEGRADED')).toBe('ADVISORY');
  });

  it('scales high and medium criticality between the two', () => {
    expect(assetBlockerSeverity(asset('HIGH'), 'OUT_OF_SERVICE')).toBe('MAJOR');
    expect(assetBlockerSeverity(asset('HIGH'), 'DEGRADED')).toBe('MINOR');
    expect(assetBlockerSeverity(asset('MEDIUM'), 'OUT_OF_SERVICE')).toBe('MINOR');
  });

  it('raises nothing for a healthy or decommissioned asset', () => {
    expect(assetBlockerSeverity(asset('CRITICAL'), 'OPERATIONAL')).toBeNull();
    expect(assetBlockerSeverity(asset('CRITICAL'), 'DECOMMISSIONED')).toBeNull();
  });
});
