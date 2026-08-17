import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DeviceReference, FacilityAsset, ReadinessChecklist, Site, Space, Zone } from './dto';

/**
 * The gating on the estate registers' create, edit and retire controls.
 *
 * Two rules are being pinned here, and both have cost this project a round before:
 *
 * - **A permission denial hides; a state shortfall disables with a reason.** S153 shipped a Close
 *   button that read "you do not have permission" on every job a technician ever opened.
 * - **`ARCHIVED` is terminal.** The service refuses any move out of it, so a screen that offers an
 *   edit on an archived record is offering something that can only be refused.
 */
const permits = vi.hoisted(() => vi.fn<(permission?: string) => boolean>());
vi.mock('shared/layout/actorPermissions', () => ({ permits }));

const {
  changeSiteLifecycleControl,
  createAssetControl,
  createChecklistControl,
  createSiteControl,
  createSpaceControl,
  createZoneControl,
  editAssetControl,
  editChecklistControl,
  editDeviceControl,
  editSiteControl,
  editSpaceControl,
  manageZoneMembersControl,
  registerDeviceControl,
  relocateAssetControl,
} = await import('./workflow');

const versioned = { metadata: { version: 0 } };

const site = (lifecycleStatus: Site['lifecycleStatus'] = 'ACTIVE'): Site =>
  ({ id: 's1', siteCode: 'CLET-HQ', lifecycleStatus, ...versioned }) as Site;

const space = (overrides: Partial<Space> = {}): Space =>
  ({
    id: 'r1',
    roomCode: 'HALL-A',
    lifecycleStatus: 'ACTIVE',
    readinessLocked: false,
    readinessLockedBy: null,
    ...versioned,
    ...overrides,
  }) as Space;

const asset = (lifecycleStatus: FacilityAsset['lifecycleStatus'] = 'ACTIVE'): FacilityAsset =>
  ({ id: 'a1', assetCode: 'GEN-01', lifecycleStatus, ...versioned }) as FacilityAsset;

const device = (lifecycleStatus: DeviceReference['lifecycleStatus'] = 'ACTIVE'): DeviceReference =>
  ({ id: 'd1', deviceCode: 'CAM-01', lifecycleStatus, ...versioned }) as DeviceReference;

const zone = (lifecycleStatus: Zone['lifecycleStatus'] = 'ACTIVE'): Zone =>
  ({ id: 'z1', zoneCode: 'ZONE-N', siteCode: 'CLET-HQ', lifecycleStatus, ...versioned }) as Zone;

const checklist = (
  lifecycleStatus: ReadinessChecklist['lifecycleStatus'] = 'ACTIVE',
): ReadinessChecklist =>
  ({ id: 'c1', checklistCode: 'EXAM', lifecycleStatus, ...versioned }) as ReadinessChecklist;

beforeEach(() => permits.mockReset());

describe('a permission denial hides the control rather than disabling it', () => {
  it.each([
    ['add a site', () => createSiteControl()],
    ['add a space', () => createSpaceControl()],
    ['register an asset', () => createAssetControl()],
    ['register a device', () => registerDeviceControl()],
    ['add a zone', () => createZoneControl()],
    ['add a checklist', () => createChecklistControl()],
    ['edit a site', () => editSiteControl(site())],
    ['edit a space', () => editSpaceControl(space())],
    ['edit an asset', () => editAssetControl(asset())],
    ['move an asset', () => relocateAssetControl(asset())],
    ['edit a device', () => editDeviceControl(device())],
    ['change zone membership', () => manageZoneMembersControl(zone())],
    ['edit a checklist', () => editChecklistControl(checklist())],
    ['retire a site', () => changeSiteLifecycleControl(site())],
  ])('%s is hidden without the permission', (_label, control) => {
    permits.mockReturnValue(false);
    expect(control().kind).toBe('hidden');
  });
});

describe('the permission alone is enough on a healthy record', () => {
  it.each([
    ['a site', () => editSiteControl(site())],
    ['a space', () => editSpaceControl(space())],
    ['an asset', () => editAssetControl(asset())],
    ['a device', () => editDeviceControl(device())],
    ['a zone', () => manageZoneMembersControl(zone())],
    ['a checklist', () => editChecklistControl(checklist())],
  ])('%s is editable', (_label, control) => {
    permits.mockReturnValue(true);
    expect(control().kind).toBe('allowed');
  });
});

describe('archiving is terminal, so an archived record refuses an edit', () => {
  beforeEach(() => permits.mockReturnValue(true));

  it.each([
    ['site', () => editSiteControl(site('ARCHIVED'))],
    ['space', () => editSpaceControl(space({ lifecycleStatus: 'ARCHIVED' }))],
    ['asset', () => editAssetControl(asset('ARCHIVED'))],
    ['device reference', () => editDeviceControl(device('ARCHIVED'))],
    ['zone', () => manageZoneMembersControl(zone('ARCHIVED'))],
    ['checklist', () => editChecklistControl(checklist('ARCHIVED'))],
  ])('an archived %s is disabled with a reason rather than hidden', (_label, control) => {
    const state = control();
    expect(state.kind).toBe('disabled');
    expect(state.kind === 'disabled' && state.reason).toMatch(/archiv/i);
  });

  it('will not offer to retire a site that is already archived', () => {
    const state = changeSiteLifecycleControl(site('ARCHIVED'));
    expect(state.kind).toBe('disabled');
  });

  it('still offers to retire a site that is merely inactive', () => {
    expect(changeSiteLifecycleControl(site('INACTIVE')).kind).toBe('allowed');
  });
});

describe('the readiness lock blocks a space edit, and names who holds it', () => {
  beforeEach(() => permits.mockReturnValue(true));

  it('disables the edit with the lock holder rather than hiding it', () => {
    const state = editSpaceControl(
      space({ readinessLocked: true, readinessLockedBy: 'exams.officer' }),
    );
    expect(state.kind).toBe('disabled');
    expect(state.kind === 'disabled' && state.reason).toContain('exams.officer');
  });

  it('blocks retiring a locked space too - the lock is released first, not edited around', () => {
    expect(changeSiteLifecycleControl(site()).kind).toBe('allowed');
    const state = editSpaceControl(space({ readinessLocked: true, readinessLockedBy: null }));
    expect(state.kind).toBe('disabled');
  });
});
