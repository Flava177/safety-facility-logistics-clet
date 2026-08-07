/**
 * Which platform the origin serving this bundle owns.
 *
 * <h2>Why the bundle has to ask</h2>
 *
 * The same bundle is packaged into all four jars, so it runs unchanged on 8090, 8091, 8092 and 8093.
 * Without asking, it cannot tell them apart - and the version that could not tell them apart offered
 * fleet screens from the facilities origin, which then called 8093 for their data and failed with
 * "Could not reach the Fleet & Logistics service" whenever that service was not also running. The
 * screens were never facilities' to offer.
 *
 * <p>`/api/v1/system/info` answers it. Each platform service names itself; the portal answers `ALL`.
 * One request at boot, before the first paint that depends on it, and every other scoping decision -
 * navigation, the sign-in account list, which service to ask for permissions - derives from the
 * result rather than from a port number hard-coded somewhere in the front end.
 *
 * <h2>When it cannot be reached</h2>
 *
 * `UNKNOWN`, and the caller shows an error. Guessing a platform here would be the same class of
 * mistake as the permission fail-open: a dashboard that looks healthy while showing the wrong thing.
 */
export type ServingPlatform = 'IFIMP' | 'SSEMP' | 'FTLMP' | 'ALL' | 'UNKNOWN';

let serving: ServingPlatform = 'UNKNOWN';

/** Long enough for a local service, short enough that boot is not held up by a dead one. */
const TIMEOUT_MS = 2500;

const PLATFORMS: ServingPlatform[] = ['IFIMP', 'SSEMP', 'FTLMP', 'ALL'];

export const loadServingPlatform = async (): Promise<ServingPlatform> => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    // Relative on purpose: the serving origin is the answer, so it must not go through a configured
    // base URL. A base URL would ask some other service which platform *this* one is.
    const response = await fetch('/api/v1/system/info', { signal: controller.signal });
    if (!response.ok) {
      return serving;
    }
    const body = (await response.json()) as { data?: { platform?: string } };
    const platform = body?.data?.platform;
    if (platform && (PLATFORMS as string[]).includes(platform)) {
      serving = platform as ServingPlatform;
    }
    return serving;
  } catch {
    return serving;
  } finally {
    clearTimeout(timer);
  }
};

/** The platform this origin serves. `UNKNOWN` until {@link loadServingPlatform} has resolved. */
export const servingPlatform = (): ServingPlatform => serving;

/** `true` on the unified portal, which is the only origin that shows more than one platform. */
export const isPortal = (): boolean => serving === 'ALL';

/** Whether a platform's screens belong on this origin. */
export const servesPlatform = (platform: 'IFIMP' | 'SSEMP' | 'FTLMP'): boolean =>
  serving === 'ALL' || serving === platform;

/** For messages the operator reads. */
export const servingPlatformName = (): string =>
  ({
    IFIMP: 'Facilities & Infrastructure',
    SSEMP: 'Safety, Security & Emergency',
    FTLMP: 'Fleet, Transport & Logistics',
    ALL: 'SFL Operations',
    UNKNOWN: 'this service',
  })[serving];
