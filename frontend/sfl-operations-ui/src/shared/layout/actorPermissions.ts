import { apiClient } from 'shared/api/client';
import type { SflPermission } from './permissions';

/**
 * What the actor is permitted to do, asked of the services rather than guessed.
 *
 * ## Why this is fetched and not derived
 *
 * Programme and system entitlement are derived from roles in `programmeModel.ts`, because those two
 * mappings are small enough to transcribe and check. Permissions are not: there are **103 permissions
 * across 26 roles**, held in four matrices. Copying that into TypeScript would guarantee the drift the
 * whole idea is meant to prevent — a sidebar that eventually offers a screen the service refuses, or
 * hides one it allows. So each service answers for its own matrices at `/actor/permissions`.
 *
 * ## Why it is resolved before the first render
 *
 * The navigation, the route guard and the landing destination are all synchronous. Making them await a
 * fetch would mean either a context threaded through four call sites or a sidebar that renders wide and
 * then narrows — and the flicker is worse than the wait, because a nav entry that appears and vanishes
 * looks like a bug rather than a permission. `main.tsx` resolves this once, then renders.
 *
 * ## What happens when it cannot be answered
 *
 * **Nothing is narrowed.** A null result — service down, request timed out, response malformed — means
 * this returns `true` for everything, so the dashboard behaves exactly as it did before item-level
 * gating existed. That is the same fail-open choice made for system entitlement and for the same
 * reason: a dashboard that hides screens because a request failed reads as a broken build, and the
 * services refuse anything the actor cannot do regardless. Hiding a nav entry has never been the
 * enforcement point.
 *
 * The emergency service being down is the ordinary case of this, since only the fleet service is
 * usually running. Its permissions simply go unknown and S174 items stay visible.
 *
 * ## Why every service must be listed here
 *
 * The fail-open above is per-*set*, not per-service: as soon as **one** source answers, `granted` is
 * non-null and anything absent from it is treated as denied. So a service missing from `SOURCES`
 * does not go "unknown" — it goes **denied**, and every one of its gated controls silently
 * disappears while the dashboard looks perfectly healthy.
 *
 * That is exactly what happened when S152 arrived: fleet answered, facilities was not asked, and so
 * every facilities permission evaluated false — the dashboard drilldowns stopped navigating and the
 * lock and mode controls vanished, with no error anywhere. **Adding a module means adding its source
 * here.**
 */

/** Long enough for a local service, short enough that a dead one does not hold up the first paint. */
const TIMEOUT_MS = 2500;

let granted: Set<string> | null = null;

interface Source {
  path: string;
  service?: 'emergency' | 'facilities';
}

const SOURCES: Source[] = [
  // Fleet, fuel and dispatch — three matrices, one deployable, one answer.
  { path: '/api/v1/fleet/actor/permissions' },
  // S174 is its own deployable with its own matrix (ADR 0004), so it answers separately.
  { path: '/api/v1/emergency/actor/permissions', service: 'emergency' },
  // S152, S153 and S159 — the IFIMP deployable, one matrix in `shared` answering for all three.
  { path: '/api/v1/facilities/actor/permissions', service: 'facilities' },
];

const fetchOne = async (source: Source): Promise<string[]> => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const result = await apiClient.get<string[]>(
      source.path,
      undefined,
      controller.signal,
      source.service,
    );
    return Array.isArray(result) ? result : [];
  } catch {
    // Deliberately silent. An unavailable service means "unknown", not "denied" — see the docblock.
    return [];
  } finally {
    clearTimeout(timer);
  }
};

/**
 * Resolves the actor's permissions. Never throws, never rejects.
 *
 * Leaves the set null when **every** source failed, which is what makes the fail-open default kick in.
 * A partial answer is still an answer: if fleet replies and emergency does not, the fleet permissions
 * narrow fleet items and the S174 items stay visible because nothing is known about them.
 */
export const loadActorPermissions = async (): Promise<void> => {
  const results = await Promise.all(SOURCES.map(fetchOne));
  const merged = results.flat();
  granted = merged.length > 0 ? new Set(merged) : null;
};

/**
 * Whether a nav item may be offered.
 *
 * An item with no `permission` is always offered — its section's system entitlement is the whole
 * requirement, which is true of most screens.
 */
/**
 * Whether a control may be offered.
 *
 * Takes `SflPermission` rather than `string` deliberately: a mistyped permission returns false and
 * hides the control **permanently and silently**, which presents as a missing feature rather than as
 * an error. The union turns that into a compile error.
 */
export const permits = (permission?: SflPermission): boolean => {
  if (!permission) {
    return true;
  }
  if (granted === null) {
    return true;
  }
  return granted.has(permission);
};

/** For the account panel, so the actor can see what the dashboard was told. */
export const resolvedPermissionCount = (): number | null => (granted === null ? null : granted.size);
