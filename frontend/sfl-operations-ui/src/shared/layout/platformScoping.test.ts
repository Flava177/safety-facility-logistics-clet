import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * A service origin offers its own platform and no other.
 *
 * <h2>The defect this exists to stop coming back</h2>
 *
 * <p>The same bundle is packaged into four jars, so it runs unchanged on 8090, 8091, 8092 and 8093.
 * Until it asked which one was serving it, it offered every platform's screens from every origin -
 * and the fleet screens, opened from the facilities service, called port 8093 for their data. When
 * that service happened to be running it worked; when it did not, the operator got "Could not reach
 * the Fleet & Logistics service at http://localhost:8093" from a dashboard they had opened to look
 * at facilities. It read as intermittent. It was not: it was deterministic on something the operator
 * had no reason to connect it to.
 *
 * <p>The fix is one filter, and this is the test that keeps it. Nothing else in the suite would
 * notice its removal, because every other test mocks the platform away to look at a different rule.
 */

const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({
  permits,
  permitsAny: () => true,
  actorIsReviewer: () => true,
}));

const serving = vi.hoisted(() => ({ value: 'ALL' as string }));
vi.mock('shared/platform', () => ({
  servesPlatform: (platform: string) => serving.value === 'ALL' || serving.value === platform,
  isPortal: () => serving.value === 'ALL',
  servingPlatform: () => serving.value,
  servingPlatformName: () => 'test service',
}));

vi.mock('shared/layout/programmes', async () => {
  const model = await vi.importActual<typeof import('./programmeModel')>('./programmeModel');
  return { ...model, entitledTo: () => true, entitledToSystem: () => true, portalLabel: () => 'test' };
});

vi.mock('shared/layout/personas', () => ({ isPersona: () => true, PersonaCode: {} }));

const { entitledSections } = await import('./navigation');

const programmesOffered = (): string[] => [
  ...new Set(entitledSections().map((section) => section.programme)),
];

beforeEach(() => {
  // Entitlement and capability are somebody else's subject here: grant everything, so the only
  // thing that can remove a section is the origin.
  permits.mockReturnValue(true);
  serving.value = 'ALL';
});

describe('scoping by serving origin', () => {
  it('the facilities service offers IFIMP alone', () => {
    serving.value = 'IFIMP';
    expect(programmesOffered()).toEqual(['IFIMP']);
  });

  it('the safety-security service offers SSEMP alone', () => {
    serving.value = 'SSEMP';
    expect(programmesOffered()).toEqual(['SSEMP']);
  });

  it('the fleet service offers FTLMP alone', () => {
    serving.value = 'FTLMP';
    expect(programmesOffered()).toEqual(['FTLMP']);
  });

  it('the portal offers all three', () => {
    serving.value = 'ALL';
    expect(programmesOffered().sort()).toEqual(['FTLMP', 'IFIMP', 'SSEMP']);
  });

  it('an origin that never answered offers nothing', () => {
    // UNKNOWN owns no platform. Guessing one would be the same mistake as the permission fail-open:
    // a dashboard that looks healthy while showing the wrong thing.
    serving.value = 'UNKNOWN';
    expect(programmesOffered()).toEqual([]);
  });
});
