/**
 * Runtime configuration for the SFL API clients.
 *
 * In local development the services accept the `X-SFL-*` actor headers (see
 * `FleetActorResolver`); in production the same `ActorContext` is built from the OIDC/JWT
 * principal instead and these headers are ignored. Everything is env-driven so no environment
 * detail is compiled into a component.
 */

const readEnv = (key: string, fallback: string): string => {
  const value = import.meta.env[key as keyof ImportMetaEnv] as string | undefined;
  return value === undefined || value === '' ? fallback : value;
};

export interface SflActorConfig {
  user: string;
  displayName: string;
  roles: string;
  sites: string;
  sourceChannel: string;
}

export const fleetApiBaseUrl = readEnv('VITE_FLEET_API_BASE_URL', 'http://localhost:8093');

export const sflActor: SflActorConfig = {
  user: readEnv('VITE_SFL_USER', 'fleet.operator'),
  displayName: readEnv('VITE_SFL_DISPLAY_NAME', 'Fleet Operator'),
  roles: readEnv('VITE_SFL_ROLES', 'FLEET_MANAGER,FLEET_DISPATCHER,FLEET_AUDITOR'),
  sites: readEnv('VITE_SFL_SITES', 'CLET-HQ'),
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
