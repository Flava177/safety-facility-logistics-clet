import { keycloakClientId, keycloakIssuer } from 'shared/api/config';
import { SflSession, clearSession, readSession, sessionFromTokens, writeSession } from './session';

/**
 * Signing in against the realm.
 *
 * <h2>Why the password grant rather than a redirect</h2>
 *
 * `sfl-operations-ui` is declared in `deploy/keycloak/sfl-realm.json` as a public client with
 * `directAccessGrantsEnabled: true`, so the dashboard can exchange an email and password for a token
 * directly. That is what makes an in-app login form possible at all.
 *
 * It is worth being clear that this is **not** the flow to ship to production. The OAuth working
 * group deprecates the resource-owner password grant for public clients, for a reason that applies
 * here: the dashboard handles the user's actual password, so it cannot support multi-factor,
 * step-up, or an external identity provider, and every one of those is a plausible CLET requirement.
 * Authorization Code with PKCE is the flow that replaces it, and it needs a redirect URI, a callback
 * route and PKCE state — none of which exists yet.
 *
 * The grant is used here because the realm already enables it and it makes A1's authentication
 * reachable from a browser today. `docs/frontend/` records the migration to PKCE as owed work rather
 * than pretending this is finished.
 *
 * <h2>Errors say which of the two things went wrong</h2>
 *
 * "Cannot sign in" covers two completely different situations — the credentials are wrong, or the
 * identity provider is not running — and on a developer laptop it is nearly always the second. They
 * are reported separately, because telling somebody their password is wrong when Keycloak is simply
 * down sends them to reset a password that was fine.
 */

const TIMEOUT_MS = 15_000;

export type SignInFailure =
  | { reason: 'credentials'; message: string }
  | { reason: 'unreachable'; message: string }
  | { reason: 'disabled'; message: string }
  | { reason: 'unexpected'; message: string };

export type SignInResult = { ok: true; session: SflSession } | ({ ok: false } & SignInFailure);

const tokenEndpoint = (): string => `${keycloakIssuer.replace(/\/$/, '')}/protocol/openid-connect/token`;

const form = (fields: Record<string, string>): string =>
  Object.entries(fields)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');

interface TokenResponse {
  access_token?: string;
  refresh_token?: string;
  error?: string;
  error_description?: string;
}

/**
 * Exchanges an email and password for a session.
 *
 * Never throws: every caller is a form, and a form that throws takes the page with it.
 */
export const signIn = async (email: string, password: string): Promise<SignInResult> => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(tokenEndpoint(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form({
        grant_type: 'password',
        client_id: keycloakClientId,
        username: email.trim(),
        password,
        scope: 'openid profile email',
      }),
      signal: controller.signal,
    });
  } catch {
    // A network-level failure. On a laptop this is almost always Keycloak not running, so the
    // message names that first rather than blaming the credentials.
    return {
      ok: false,
      reason: 'unreachable',
      message:
        `Could not reach the identity provider at ${keycloakIssuer}. ` +
        'Start Keycloak, or run the service with SFL_SECURITY_ENABLED=false for header-based local development.',
    };
  } finally {
    clearTimeout(timer);
  }

  let body: TokenResponse = {};
  try {
    body = (await response.json()) as TokenResponse;
  } catch {
    body = {};
  }

  if (!response.ok) {
    if (response.status === 401 || body.error === 'invalid_grant') {
      return {
        ok: false,
        reason: 'credentials',
        message: 'That email and password do not match an account.',
      };
    }
    if (body.error === 'invalid_client' || body.error === 'unauthorized_client') {
      // The realm exists but the client is misconfigured — a deployment problem, not a user one.
      return {
        ok: false,
        reason: 'disabled',
        message:
          `The realm refused this client (${keycloakClientId}). ` +
          'Check that the client exists and has direct access grants enabled.',
      };
    }
    return {
      ok: false,
      reason: 'unexpected',
      message: body.error_description ?? `Sign-in failed (HTTP ${response.status}).`,
    };
  }

  if (!body.access_token) {
    return { ok: false, reason: 'unexpected', message: 'The realm returned no access token.' };
  }

  const session = sessionFromTokens(body.access_token, body.refresh_token ?? null);
  if (!session) {
    return { ok: false, reason: 'unexpected', message: 'The access token could not be read.' };
  }

  writeSession(session);
  return { ok: true, session };
};

/**
 * Ends the session here, and tells the realm to end it there too.
 *
 * The local clear happens **first and unconditionally**. If the realm is unreachable, the user is
 * still signed out of this browser, which is the half that matters to the person standing at the
 * workstation.
 */
export const signOut = async (): Promise<void> => {
  const session = readSession();
  clearSession();
  if (!session?.refreshToken) {
    return;
  }
  try {
    await fetch(`${keycloakIssuer.replace(/\/$/, '')}/protocol/openid-connect/logout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form({ client_id: keycloakClientId, refresh_token: session.refreshToken }),
    });
  } catch {
    // Best effort. The token expires on its own, and this browser has already forgotten it.
  }
};
