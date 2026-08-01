# Signing in, and the seeded accounts

## Running it

```powershell
docker compose -f deploy\compose\docker-compose.microservices.yml up -d keycloak
.\start-fleet.ps1 -WithLogin
```

Then `http://localhost:8093/ui/` → the sign-in page.

`-WithLogin` flips **both** halves together, and they have to move together: it sets
`SFL_SECURITY_ENABLED=true` on the service and builds the dashboard with
`VITE_SFL_AUTH_REQUIRED=true`. Setting either alone gives you a dashboard that cannot authenticate,
or a login form guarding a service that does not want one.

Without the switch nothing changes: `.\start-fleet.ps1` runs security-off with the `X-SFL-*` headers
and the development actor switcher, exactly as before.

## The accounts

One password for all of them: **`Password@Clet1`**

| Email | Role | Sees |
|---|---|---|
| `fleetmanager@clet.gh` | `FLEET_MANAGER` | Fleet, fuel, dispatch |
| `driver@clet.gh` | `FLEET_DRIVER` | My driving day, trips, vehicles |
| `fleetofficer@clet.gh` | `FLEET_LOGISTICS_OFFICER` | Fleet, fuel, dispatch |
| `reportingviewer@clet.gh` | `FLEET_REPORTING_VIEWER` | Fleet, fuel, dispatch — read only |
| `dispatchcontroller@clet.gh` | `DISPATCH_CONTROLLER` | Dispatch |
| `logisticscoordinator@clet.gh` | `LOGISTICS_COORDINATOR` | Dispatch |
| `mailroomofficer@clet.gh` | `MAILROOM_OFFICER` | Mailroom |
| `centremanager@clet.gh` | `CENTRE_MANAGER` | Centre receipts, facilities, booking |
| `facilitiesmanager@clet.gh` | `FACILITIES_MANAGER` | Facilities, maintenance, booking |
| `facilitiesdirector@clet.gh` | `FACILITIES_DIRECTOR` | All of IFIMP, all sites |
| `maintenancesupervisor@clet.gh` | `IFIMP_MAINTENANCE_SUPERVISOR` | Maintenance, readiness |
| `technician@clet.gh` | `IFIMP_TECHNICIAN` | My work queue, turnaround |
| `vendortechnician@clet.gh` | `VENDOR_TECHNICIAN` | Their assigned work orders only |
| `requester@clet.gh` | `IFIMP_REQUESTER` | My requests, room booking |
| `emergencycoordinator@clet.gh` | `EMERGENCY_COORDINATOR` | Emergency notification |
| `securityofficer@clet.gh` | `SECURITY_OFFICER` | Dispatch exceptions, emergency |
| `socoperator@clet.gh` | `SOC_OPERATOR` | Emergency notification |
| `hsemanager@clet.gh` | `HSE_MANAGER` | Facilities, maintenance, emergency |
| `command@clet.gh` | `COMMAND_ROLE` | Everything, all sites |
| `auditor@clet.gh` | `AUDITOR` | Read and prove, every programme |
| `complianceofficer@clet.gh` | `COMPLIANCE_OFFICER` | Auditor plus export approval |
| `sfladmin@clet.gh` | `SFL_ADMIN` | Everything |

Every account is scoped to `CLET-HQ` except the director, command and admin, which hold `*`.

**These are development credentials in a checked-in realm file.** One shared password across
twenty-two accounts is a seeding convenience, not a security posture, and this realm must not be
imported into any environment reachable from outside a laptop.

## What signing in actually does

The dashboard exchanges the email and password for a token at the realm's token endpoint, stores it
for the tab, and sends it as `Authorization: Bearer` on every call thereafter.

**The roles come from the token, not from the form.** `realm_access.roles` and `site_scopes` are read
out of the token's own claims — the same two the services read — so what the sidebar shows and what
the service enforces come from one signed source. That is the whole reason this was built rather than
gating the existing header-based actor behind a login screen: the headers are caller-supplied, so a
"signed-in driver" could otherwise still assert `SFL_ADMIN` by editing storage.

## What this closed

A1 turned authentication on across every service — resource server, JWT actor resolvers, realm, the
403-versus-401 distinction. **None of it was reachable from a browser.** The API client sent
`X-SFL-*` and no `Authorization` header at any point, so the dashboard only worked against a service
running with security off, and a service running with the secure default answered 401 to everything
the dashboard did. This is the missing half.

## Owed work

**The password grant is not the flow to ship.** The dashboard handles the user's actual password, so
it cannot support multi-factor, step-up authentication, or an external identity provider — and all
three are plausible CLET requirements. The OAuth working group deprecates this grant for public
clients for exactly that reason. It is used here because the realm already enables it and it makes
authentication reachable today.

Authorization Code with PKCE is the replacement. It needs a redirect URI on the client, a callback
route, PKCE challenge/verifier state, and silent renewal — none of which exists. Until then:

- **No token refresh.** The refresh token is stored but never exchanged, so a session ends when the
  access token expires and the operator signs in again. `readSession` clears an expired token rather
  than sending it, so the failure is a redirect to sign-in and not a wall of 401s.
- **The token is in `sessionStorage`**, which a successful XSS can read. An `HttpOnly` cookie would
  be safer and needs a backend-for-frontend this platform does not have. Recorded rather than glossed.
