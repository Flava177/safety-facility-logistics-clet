# ADR 0008 - Authentication is a provider behind one switch

- Status: **Accepted and implemented**, 6 August 2026. Both providers ship; the switch defaults to the
  token path.
- Date: 2026-08-06
- Deciders: SFL platform / F&L directorate
- Relates: [0005 programme-scoped portals](0005-programme-scoped-portals-and-navigation-entitlement.md);
  [0006 one dashboard](0006-one-dashboard-and-the-retirement-of-the-per-service-pages.md);
  [0007 row-level security](0007-row-level-security-deferred-with-a-named-mechanism.md)

## Context

The dashboard has always had two ways to sign somebody in.

**A seeded sign-in.** Twenty-two accounts and one shared password, checked in the browser, which then
becomes the actor for the session. It is not authentication and does not pretend to be: no token is
issued, nothing is verified, and the credentials are in the bundle served to the browser. It is
nonetheless the right thing locally, because the services it talks to run with `sfl.security.enabled=false`
and take the actor from `X-SFL-*` headers - so a sign-in page there can only ever decide *which headers
to send*, and a form that looked like it authenticated would be the worse outcome.

**A token exchange.** The same twenty-two accounts with the same addresses and password exist in a realm
seed under `deploy/idp/`, and a password-grant exchange against the issuer produces a real token
carrying roles and `site_scopes` - the claim ADR 0007's policies are written against.

Both worked. What did not work was **choosing between them**: the two were reachable only by importing
one module or the other, so "which one is in use" was a property of whichever import somebody had
happened to write. That is not a configuration; it is a fact you discover by reading the call site. A
build could ship the seeded path to production and nothing in the code would look wrong.

There is a second reason this needed a seam, and it is the one that will matter longer. The identity
provider behind the token path **is expected to change**. Committing every call site to one product's
client library, and its vocabulary, would make that change a migration through the application instead
of a change to its configuration.

## Decision

**Sign-in is a provider, selected by one environment variable, and nothing downstream knows a provider
exists.**

1. `VITE_AUTH_PROVIDER` takes `seeded` or `oidc`. `shared/auth/provider.ts` exposes
   `signInWithConfiguredProvider()` and `signOutOfConfiguredProvider()`; the sign-in page and the
   account panel call those and nothing else.
2. **The default is the token path.** `seeded` is chosen explicitly or not at all. A build that forgot
   to set the variable gets the real path and fails loudly against a missing issuer, rather than
   quietly accepting a password that is printed on the sign-in page. Failing towards the stricter of
   two options is the only defensible default here.
3. **The token path is configured, not coded.** An issuer URL (`VITE_SFL_IAM_ISSUER`) and a client id
   (`VITE_SFL_IAM_CLIENT_ID`), and no product name anywhere in the code path, the module names, the
   directory names or the environment variables. The realm seed lives at `deploy/idp/`, the module is
   `oidc.ts`, the configuration reads `IAM`.
4. **Everything after sign-in is already provider-blind, and stays that way.** `session.ts` stores both
   kinds of session identically, and the API client reads only the session. The abstraction is one file
   wide because that is all the surface either path ever had.
5. **Removable, not just swappable.** Deleting the token path leaves a working dashboard, and deleting
   the seeded path leaves a working dashboard. Neither imports the other; `provider.ts` imports both
   and is the only file that does.

## What this deliberately does not decide

- **Which identity provider.** That is a procurement and platform decision, and naming one in this
  record would embed it in the architecture, which is the thing being avoided. What is decided is the
  shape of the seam.
- **The grant type.** The token path uses a password grant, which suits a first-party dashboard against
  a development realm and is not the right long-term answer for a browser client. Moving to
  authorisation code with PKCE changes `oidc.ts` and touches nothing else - which is the property this
  record exists to buy.

## Consequences

- Which sign-in a build uses is now visible in one line of one file, and reviewable in a diff. It was
  previously visible only to somebody who traced the imports.
- The seeded path's honesty is preserved rather than dressed up. Its docblock states plainly that it is
  not authentication, and the sign-in page lists the accounts it will accept - which is only reasonable
  because they are development accounts and the list is scoped to the platform serving the page
  (ADR 0005, amendment of 6 August).
- **One known gap, stated rather than left to be discovered.** The token and logout endpoints are
  currently derived from the issuer by appending the development realm's endpoint layout, rather than
  read from the issuer's discovery document. That is provider-shaped, and a provider whose endpoints sit
  elsewhere would need `oidc.ts` changed. Reading `/.well-known/openid-configuration` closes it; it is a
  contained change in one module and is not blocking anything today.
- The renaming that preceded this is part of the same decision, not tidying. The module, its deploy
  directory and its two configuration symbols were all named after the product that happened to be
  running behind them; they are now named after the protocol and the role - `oidc.ts`, `deploy/idp/`,
  `iamIssuer`, `iamClientId`. A symbol named after a product is a dependency on that product which no
  compiler will ever flag.
