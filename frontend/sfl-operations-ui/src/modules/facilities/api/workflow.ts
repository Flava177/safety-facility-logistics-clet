import { permits } from 'shared/layout/actorPermissions';
import type { FacilityAsset, ReadinessBlocker, Site, Space } from './dto';
import type { BlockerSeverity, LocationReadinessStatus } from './enums';

/**
 * What an actor may do next, and what the estate's own state allows.
 *
 * Two different questions, answered together because a screen has to combine them before it renders
 * a button:
 *
 * - **May this actor?** — `permits`, backed by `GET /actor/permissions`, which is the service's own
 *   answer rather than a guess from role names.
 * - **May this record?** — the same rules the service enforces, evaluated locally so a control that
 *   would be refused is disabled with a reason instead of failing on click.
 *
 * This is a usability layer and never the enforcement point: every one of these calls is authorised
 * again server-side, and the service refuses regardless of what the screen offered.
 */

/** A control the screen may render, with why it is unavailable when it is. */
export interface Action {
  allowed: boolean;
  /** Present when `allowed` is false — shown as the disabled control's tooltip. */
  reason?: string;
}

const allow: Action = { allowed: true };
const deny = (reason: string): Action => ({ allowed: false, reason });

const NO_PERMISSION = 'You do not have permission for this action.';

// ---- sites ------------------------------------------------------------------------------------

export const canManageSites = (): boolean => permits('FACILITIES_SITE_MANAGE');

/**
 * Whether this actor may declare or stand down examination mode.
 *
 * Deliberately not held by `FACILITIES_MANAGER`: declaring an examination is a centre-level
 * operational decision, so the permission sits with the centre manager, command and the director.
 */
export const changeOperatingModeAction = (site: Site): Action => {
  if (!permits('FACILITIES_OPERATING_MODE_CHANGE')) {
    return deny(NO_PERMISSION);
  }
  if (site.lifecycleStatus !== 'ACTIVE') {
    return deny(`A ${site.lifecycleStatus.toLowerCase()} site cannot change operating mode.`);
  }
  return allow;
};

// ---- spaces -----------------------------------------------------------------------------------

/**
 * Whether a space's attributes may be edited.
 *
 * The readiness lock is the interesting case: while it is engaged the service refuses the edit, and
 * the way through is to release the lock — an audited act — rather than to edit around it.
 */
export const updateSpaceAction = (space: Space): Action => {
  if (!permits('FACILITIES_SPACE_MANAGE')) {
    return deny(NO_PERMISSION);
  }
  if (space.readinessLocked) {
    return deny(
      `Locked for examination use by ${space.readinessLockedBy ?? 'an officer'}. Release the lock first.`,
    );
  }
  if (space.lifecycleStatus === 'ARCHIVED') {
    return deny('Archived spaces cannot be edited.');
  }
  return allow;
};

/**
 * Whether a readiness status may be set by hand, for a given target.
 *
 * The one rule the whole module turns on: READY is refused while a critical blocker is open. Checked
 * here so the option is disabled with the count rather than offered and then refused.
 */
export const setReadinessAction = (
  target: LocationReadinessStatus,
  openBlockers: ReadinessBlocker[],
): Action => {
  if (!permits('FACILITIES_READINESS_ASSESS')) {
    return deny(NO_PERMISSION);
  }
  if (target !== 'READY') {
    return allow;
  }
  const critical = openBlockers.filter(
    (blocker) => !blocker.resolved && blocker.severity === 'CRITICAL',
  ).length;
  return critical > 0
    ? deny(`${critical} critical blocker${critical === 1 ? '' : 's'} must be resolved first.`)
    : allow;
};

export const lockAction = (space: Space): Action => {
  if (!permits('FACILITIES_READINESS_OVERRIDE')) {
    return deny(NO_PERMISSION);
  }
  return space.readinessLocked ? deny('This space is already locked.') : allow;
};

export const unlockAction = (space: Space): Action => {
  if (!permits('FACILITIES_READINESS_OVERRIDE')) {
    return deny(NO_PERMISSION);
  }
  return space.readinessLocked ? allow : deny('This space is not locked.');
};

// ---- assets -----------------------------------------------------------------------------------

export const changeAssetStatusAction = (asset: FacilityAsset): Action => {
  if (!permits('FACILITIES_ASSET_MANAGE')) {
    return deny(NO_PERMISSION);
  }
  if (asset.lifecycleStatus === 'ARCHIVED') {
    return deny('Archived assets cannot change status.');
  }
  return allow;
};

/**
 * The readiness consequence of putting an asset into a state.
 *
 * Shown before the change is confirmed, because "this generator is out of service" and "Examination
 * Hall A is now blocked" are the same fact and an operator should see both before committing to it.
 * Mirrors `ReadinessApplicationService.severityFor`.
 */
export const assetBlockerSeverity = (
  asset: FacilityAsset,
  status: FacilityAsset['operationalStatus'],
): BlockerSeverity | null => {
  const impairs =
    status === 'DEGRADED' || status === 'UNDER_MAINTENANCE' || status === 'OUT_OF_SERVICE';
  if (!impairs) {
    return null;
  }
  const total = status === 'OUT_OF_SERVICE';
  switch (asset.criticality) {
    case 'CRITICAL':
      return total ? 'CRITICAL' : 'MAJOR';
    case 'HIGH':
      return total ? 'MAJOR' : 'MINOR';
    case 'MEDIUM':
      return 'MINOR';
    default:
      return 'ADVISORY';
  }
};

// ---- readiness --------------------------------------------------------------------------------

export const canAssessReadiness = (): boolean => permits('FACILITIES_READINESS_ASSESS');
export const canManageChecklists = (): boolean => permits('FACILITIES_READINESS_CHECKLIST_MANAGE');
export const canManageAssets = (): boolean => permits('FACILITIES_ASSET_MANAGE');
export const canManageSpaces = (): boolean => permits('FACILITIES_SPACE_MANAGE');
export const canManageZones = (): boolean => permits('FACILITIES_ZONE_MANAGE');
export const canRegisterDevices = (): boolean => permits('FACILITIES_DEVICE_REFERENCE_REGISTER');
export const canDrillDown = (): boolean => permits('FACILITIES_DASHBOARD_DRILLDOWN');
export const canVerifyAuditChain = (): boolean => permits('FACILITIES_AUDIT_INTEGRITY_CHECK');
export const canManageConfiguration = (): boolean => permits('FACILITIES_CONFIG_MANAGE');
