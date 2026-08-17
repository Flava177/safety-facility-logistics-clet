import { apiClient } from 'shared/api/client';
import { sflActor } from 'shared/api/config';
import { servingPlatform } from 'shared/platform';

/**
 * The site codes this actor may actually name, with `*` resolved to real ones.
 *
 * <h2>What `*` was doing, and why it had to stop</h2>
 *
 * <p>`X-SFL-Sites` carries either a list of site codes or the single token `*`, which the services
 * read as "every site". The dashboard read it as a site code. So a director scoped to `*` filtered
 * every register with `siteCode=*`, which matches no row, and the whole application rendered empty
 * for the one account entitled to see all of it - two spaces, one bookable resource and a site
 * readiness of "0 of 0", none of it missing and none of it visible. Sites was the only register with
 * content, because `GET /sites` takes no site parameter.
 *
 * <p>The write half was worse. `SiteSelect` offered `*` as the only option in a create dialog, the
 * service accepted it, and the record was filed against a site that does not exist. That is verified
 * behaviour, not a worry: `POST /bookable-resources` with `siteCode: "*"` answers 201.
 *
 * <h2>The two jobs a default site does</h2>
 *
 * A filter default and a form default are not the same value, and conflating them is what let one
 * token serve both:
 *
 * <ul>
 *   <li><b>Filtering</b> - "every site" is the <em>absence</em> of a constraint. {@link defaultSite}
 *       is therefore empty for a wildcard scope, and the pages already send `siteCode || undefined`.
 *   <li><b>Writing</b> - a request field needs a real code. There is no honest default, so a
 *       wildcard-scoped actor is offered the resolved list and picks; {@link actorSites} is what
 *       populates it.
 * </ul>
 *
 * <h2>Where the real list comes from</h2>
 *
 * <p>The facilities service owns the site register, so IFIMP - and the portal, which serves
 * everything - can resolve the wildcard by asking it. The other two platforms have no site register
 * of their own, and rather than cross-call a service this origin does not own (the mistake
 * `actorPermissions` was changed to stop making), an unresolvable wildcard leaves the list empty and
 * {@link actorSiteFailure} says so. An empty select with an explanation is a smaller failure than one
 * offering a value the service will mis-file.
 */

const WILDCARD = '*';

/** Long enough for a local service, short enough that a dead one does not hold up the first paint. */
const TIMEOUT_MS = 2500;

const declared: string[] = sflActor.sites
  .split(',')
  .map((site) => site.trim())
  .filter(Boolean);

/** Whether this actor's scope is "every site" rather than a list of them. */
export const scopeIsEverySite: boolean = declared.includes(WILDCARD);

const named: string[] = declared.filter((site) => site !== WILDCARD);

let resolved: string[] = named;
let failure: string | null = null;

/**
 * The site a filter opens on.
 *
 * Empty for a wildcard scope, because "every site" is no filter at all - and empty for no scope,
 * because an actor with no sites has nothing to narrow to. Otherwise the first site named.
 *
 * A plain string rather than a function: it is read as `useState(defaultSite)` at forty call sites,
 * and it needs no network to be correct.
 */
export const defaultSite: string = scopeIsEverySite ? '' : (named[0] ?? '');

/**
 * Resolves a wildcard scope into real site codes. Never throws, never rejects.
 *
 * A no-op for an actor whose scope is already a list, which is most of them - so this costs a
 * request only for the directors, auditors and administrators who hold `*`.
 */
export const loadActorSites = async (): Promise<void> => {
  if (!scopeIsEverySite) {
    return;
  }

  const platform = servingPlatform();
  if (platform !== 'IFIMP' && platform !== 'ALL') {
    failure =
      'Your scope is every site, and this service does not hold the site register, so no site can be ' +
      'chosen here. Open the facilities service to work with a specific site.';
    return;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const sites = await apiClient.get<{ siteCode: string }[]>(
      '/api/v1/facilities/sites',
      undefined,
      controller.signal,
      'facilities',
    );
    const codes = (Array.isArray(sites) ? sites : [])
      .map((site) => site?.siteCode)
      .filter((code): code is string => typeof code === 'string' && code.length > 0);
    // Union rather than replacement: a scope of "CLET-HQ,*" is legal and both halves are real.
    resolved = Array.from(new Set([...named, ...codes])).sort();
    failure = resolved.length === 0 ? 'The site register is empty, so no site can be chosen.' : null;
  } catch {
    failure =
      'The site register could not be read, so the list of sites is incomplete. Anything you create ' +
      'here would be filed against the wrong site.';
  } finally {
    clearTimeout(timer);
  }
};

/** Every site this actor may name, wildcard resolved. Empty when it could not be. */
export const actorSites = (): string[] => resolved;

/** The sentence to show beneath a site control when the list is not trustworthy, or `null`. */
export const actorSiteFailure = (): string | null => failure;
