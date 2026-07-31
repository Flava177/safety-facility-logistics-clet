# Role portals — gap report

**Status (31 July 2026): six personal landings built, four roles confirmed as already served, two
Deviations recorded rather than invented.** Frontend runs 84 tests (was 73), clean typecheck and build.

The trace matrix — all 26 roles, one row each — is
[`SFL_Role_Portal_Trace_Matrix.md`](SFL_Role_Portal_Trace_Matrix.md). This is what building against
it found.

---

## 1. The defect this pass exists to fix

**Every module in this dashboard was built for the operator, and eight roles hold real permissions
with no view designed for them.** They signed in, landed on somebody else's dashboard, and saw mostly
hidden controls — which reads as a broken build rather than as a role boundary.

`landingPath()` returns the first item of the first entitled section, and until now the first section
was always an operator's. A driver's first screen was the facilities or fleet dashboard.

---

## 2. What a permission cannot say, and the one place this dashboard now says it

**`FLEET_DRIVER` holds eight permissions and every single one is also held by `FLEET_MANAGER`.**

That is the whole problem in one sentence. There is no permission that distinguishes a driver from
the fleet office, so gating "My driving day" on `FUEL_LOGBOOK_CREATE` would have offered it to the
manager as *their* landing page. What makes somebody a driver is not what they can do — it is what
they cannot.

`shared/layout/personas.ts` encodes that, and it does **not** invent the rule. It transcribes the one
the services already enforce:

| Service rule | Persona |
|---|---|
| `FuelAccessPolicy.isDriverOnly` — `FLEET_DRIVER` and none of manager / officer / admin | `driver` |
| `FacilityFaultService.requesterFilter` — narrows only when `IFIMP_REQUESTER` is the actor's *only* facilities role | `requester` |
| `WorkOrderApplicationService.vendorFilter` — assignment, not role | `technician` |

S153 stated the reasoning and it is the reason this file exists: *"a manager who also happens to hold
the requester role is a manager; treating the union of roles as its narrowest member would make
adding a role to somebody take capability away."*

**This is not an authorisation check and must never become one.** Nothing in `personas.ts` hides
data. The services do that, per record. A wrong answer here costs a click, not a disclosure — and
`personas.test.ts` pins all eleven cases, including the ones where a persona must *not* apply.

---

## 3. Two portals that could not be built honestly

### `CENTRE_MANAGER` — built, and it says on the page that it cannot narrow

`Dispatch.destinationCentre` and `assignedHandler` are `VARCHAR(200)` free text supplied at creation,
with no relationship to a principal. There is nothing to narrow on.

Narrowing on them would produce a rule that holds whenever somebody happened to type an actor id into
the field and fails silently otherwise, which is **worse than no rule, because it looks like
enforcement**. So the screen lists consignments *at this site*, says so in the subtitle, and says it
again in an `Alert` naming the owner and the gap reference. A user who assumes they are seeing only
their own consignments would be wrong, and no screen should let somebody be wrong quietly.

**Owner: Transportation & Logistics Unit** — a principal-bound centre reference on a dispatch.
Recorded as **C-16** in `docs/fleet/S166_Gap_And_Conflict_Report.md`.

### `FLEET_DRIVER` — built narrow, recorded as a Deviation

The SRS gives the driver no §2.3 user class and every `SRS-SFL-S168fuel-*` requirement is written *"As
a Fleet or Logistics Officer"*. The portal is therefore the minimum surface the eight permissions
imply and nothing beyond it — a driver holding `FLEET_VEHICLE_READ` gets the vehicle they are
driving, not the register.

**Owner: Transportation & Logistics Unit**, to confirm whether "Driver" is a Phase 1 user class at
all. Note `S168a Trip / Driver-Booking Portal` is a **Phase 2** system in the mapping, which is a
positive reason not to drift toward trip booking here.

---

## 4. What driving the data found

**A driver's fuel transactions are not "mine", and the first draft of the screen said they were.**
`FUEL_TRANSACTION_READ` is not narrowed per record — only logbooks are — so the service returns every
fill at the site. The panel is now labelled "Fuel recorded at this site" with a sentence saying it is
not filtered to the reader. Calling it "my fuel" over a list containing a colleague's fill would be a
lie the screen tells on the service's behalf.

**The facilities API returns arrays, the fuel and dispatch APIs return pages.** Caught by the
typechecker rather than at runtime, but worth recording: `searchFaults` and `searchWorkOrders` return
`T[]`, while `driverLogbooksApi.search` and the dispatch clients return `FuelPageResponse<T>` /
`DispatchPageResponse<T>`. A portal composing across modules meets both shapes, and there is no
shared page type to lean on.

---

## 5. Not built, and why

**No new deployable, so `SOURCES` in `actorPermissions.ts` is untouched.** Every portal reads from a
service already listed there. This is stated explicitly because the rule — *adding a module means
adding its permissions source* — has cost this platform a day once, and the temptation is to check
only when something feels new. Nothing here is new to that file, and that was verified rather than
assumed.

**Dispatch controller and logistics coordinator get no portal.** Both hold the full 11-permission
controller set and the existing dispatch module already is their view. Duplicating fifteen screens to
change a title would add maintenance burden and no capability. **Recorded as a finding, which is what
the prompt asked for.**

**Command, reporting viewer and administrator landings are not built.** `COMMAND_ROLE`,
`FLEET_REPORTING_VIEWER` and `HSE_MANAGER` hold read-only sets and currently land on an operator
dashboard whose controls are hidden — which works but is not a designed answer. `SFL_ADMIN` and
`DTI_ADMIN` land on the facilities configuration screen, which is most of what an administrator
needs. Both are honest today and neither is finished; they are the next slice rather than a claim.

**The assurance view links rather than merges.** Each service hash-chains its own audit log
independently, and there are four chains. A single combined "verify" button would assert something no
component here has standing to assert, so the page is a directory with the caveat stated at the top.

**Verification was not done under authentication.** The prompt assumes A1 landed; it was deferred, so
the actor still arrives in an `X-SFL-*` header. The persona rule reads roles from that header exactly
as it will read them from a JWT claim, so the logic is unaffected — but "signed in as each role" has
not been exercised against a token, and that remains owed.

**The S159 booking half of the requester portal is a stated placeholder.** The booking API is
complete and narrows requesters per record, but S159 has no screens, so there is nowhere to navigate a
row to. The panel says that in plain words rather than rendering an empty table that would read as
"you have no bookings".
