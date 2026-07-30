import { readActorOverride } from 'shared/dev/actorOverride';

/**
 * Runtime configuration for the SFL API clients.
 *
 * In local development the services accept the `X-SFL-*` actor headers (see
 * `FleetActorResolver`); in production the same `ActorContext` is built from the OIDC/JWT
 * principal instead and these headers are ignored. Everything is env-driven so no environment
 * detail is compiled into a component.
 */

/**
 * The development actor override, when one is set.
 *
 * Read once, at module scope, because everything derived from the actor is also computed once at
 * module scope — see `shared/dev/actorOverride.ts` for why applying an override reloads the page.
 * A blank field falls through to the environment default rather than sending an empty header.
 */
const actorOverride = readActorOverride();

const readEnv = (key: string, fallback: string): string => {
  const value = import.meta.env[key as keyof ImportMetaEnv] as string | undefined;
  return value === undefined || value === '' ? fallback : value;
};

/** Distinguishes "not set" from "deliberately empty", which is how same-origin is requested. */
const readOptionalEnv = (key: string, fallback: string): string => {
  const value = import.meta.env[key as keyof ImportMetaEnv] as string | undefined;
  return value === undefined ? fallback : value;
};

export interface SflActorConfig {
  user: string;
  displayName: string;
  roles: string;
  sites: string;
  sourceChannel: string;
}

/**
 * Base URL of the Fleet service.
 *
 * An empty value means same origin — which is what the embedded build uses, because the Spring Boot
 * service serves both the API and this dashboard. `npm run dev` points at `http://localhost:8093`
 * instead, and the service allows `http://localhost:5005` as a CORS origin.
 */
export const fleetApiBaseUrl = readOptionalEnv('VITE_FLEET_API_BASE_URL', 'http://localhost:8093');

/**
 * Base URL of the Emergency Notification service.
 *
 * S174 is a **separate service on a separate port** — `sfl-emergency-notification-service`, port
 * 8095, its own schema and its own permission matrix. Fleet, fuel and dispatch all live in
 * `sfl-fleet-logistics-service`, so this is the first time the dashboards talk to two services, and
 * it is why the API client takes a base URL per call rather than reading one global.
 *
 * The emergency service allows `http://localhost:8093` (the bundled dashboard's origin) and
 * `http://localhost:5005` (`npm run dev`), so both work over CORS without a proxy. Behind a
 * gateway this becomes a same-origin path prefix and nothing else changes.
 */
export const emergencyApiBaseUrl = readOptionalEnv(
  'VITE_EMERGENCY_API_BASE_URL',
  'http://localhost:8095',
);

/**
 * The development actor's roles, when `VITE_SFL_ROLES` is not set.
 *
 * One header serves both services and each reads only the roles its own matrix knows — an
 * unrecognised name grants nothing rather than failing the request, so the two sets can sit in one
 * list. The four emergency roles are what it takes to exercise S174 end to end: the coordinator
 * composes and sends, the command role approves and records after-action approval, the auditor
 * exports, and the integration engineer replays a dead letter.
 *
 * **In production these are different people**, and the S174 screens are built on that: the approve
 * button does not appear for an actor who cannot use it. A single actor holding all of them is a
 * local-development convenience, not the design.
 *
 * This list is also what `shared/layout/programmes.ts` derives programme entitlement from, so
 * dropping the two SSEMP roles here is all it takes to see the dashboard as a fleet operator does —
 * the emergency section disappears from the sidebar and its routes are refused.
 *
 * Two earlier entries were **not real roles**: `FLEET_DISPATCHER` and `FLEET_AUDITOR` are not in
 * `SflRole`, so every service silently dropped them and they granted nothing. Replaced with the
 * real `DISPATCH_CONTROLLER` and `FLEET_REPORTING_VIEWER`. Kept in step with `.env.production`;
 * `.env` is git-ignored, so an older local one must be corrected by hand.
 */
const defaultRoles = [
  // SFL.FTLMP
  'FLEET_MANAGER',
  'DISPATCH_CONTROLLER',
  'FLEET_REPORTING_VIEWER',
  // SFL.SSEMP
  'EMERGENCY_COORDINATOR',
  'COMMAND_ROLE',
  // Cross-programme oversight and integration
  'AUDITOR',
  'INTEGRATION_ENGINEER',
].join(',');

export const sflActor: SflActorConfig = {
  user: actorOverride?.user || readEnv('VITE_SFL_USER', 'fleet.operator'),
  displayName: actorOverride?.displayName || readEnv('VITE_SFL_DISPLAY_NAME', 'Fleet Operator'),
  roles: actorOverride?.roles || readEnv('VITE_SFL_ROLES', defaultRoles),
  sites: actorOverride?.sites || readEnv('VITE_SFL_SITES', 'CLET-HQ'),
  sourceChannel: 'WEB',
};

/**
 * Development fallback switch.
 *
 * Kept `false` by default and named explicitly: no screen may present mock data as if it came from
 * the service. See `docs/fleet/S166_Frontend_Gap_Register.md` for the endpoints this affects.
 */
export const useDevelopmentFallback =
  readEnv('VITE_FLEET_DEV_FALLBACK', 'false').toLowerCase() === 'true';

/** Default page size for register tables. */
export const defaultPageSize = 25;
