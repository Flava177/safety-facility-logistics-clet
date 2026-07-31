# S152 CAFM/IWMS UI — gap report

What the dashboard does not do, what it found while being built, and what is deliberately absent.
The companion to [S152_UI_Screen_Inventory.md](S152_UI_Screen_Inventory.md), which records what was
built.

## 1. The defect this round exists to record

**Every S152 permission silently evaluated `false`, and nothing said so.**

`shared/layout/actorPermissions.ts` asks each service what the actor may do and merges the answers
into one set. Its fail-open is per-*set*, not per-service: as soon as **one** source answers,
`granted` is non-null and anything absent from it is treated as denied. S152 was not in `SOURCES`.
So fleet answered, facilities was never asked, and every `FACILITIES_*` permission resolved to
denied — the dashboard drilldowns stopped navigating, the lock and operating-mode controls vanished,
and no error appeared anywhere, in the console or the network tab. The build was green, the
typecheck was green, and 44 tests were green.

It was found by clicking a row in a browser.

Fixed by adding the source, and the docblock there now states the rule plainly: **adding a module
means adding its source here.** The failure mode is worth knowing because it will recur for S153,
S159 and every module after them, and it presents as "the screen looks fine but nothing happens".

This is the same lesson the backend round recorded, in a different layer: 135 unit tests were green
while the service could not start. Running the thing is what finds these.

## 2. Not built, and why

### 2.1 Fault reporting and work orders have no screens — S153

> **Update, 31 July 2026.** The backend half of this is now built: S153 replaced the two controllers
> named below, gave them the authorisation they did not have, and added preventive maintenance, SLA
> escalation, vendors, parts and closure evidence. See
> [S153_CMMS_Design.md](S153_CMMS_Design.md). **The screens still do not exist** — this section stays
> open until they do, and the endpoints they need are now stable and enveloped.

The static page this module retires carried a fault register and work-order controls.
`FacilityFaultController` and `WorkOrderController` serve them, but nothing in the dashboard reaches
them. That is a **real reduction in what a user can do through a screen**, and it is stated here
rather than glossed in the retirement notice.

It was left out because faults and work orders are S153 (CMMS), not S152, and building them here
would have meant designing a maintenance workflow inside a prompt scoped to the estate model and
readiness.

Two things about those two controllers were worth knowing before that round started, and both have
since been dealt with:

- They used the **pre-S152 actor model** — `X-SFL-User` rather than the resolved `ActorContext` — and
  `FacilityFaultController.findAll()` applied **no site scoping and no permission check at all**: a
  caller with any role got every fault at every site. Both are fixed in S153 and recorded as D-01 and
  D-02 in [S153_Gap_And_Conflict_Report.md](S153_Gap_And_Conflict_Report.md).
- They now speak the platform envelope. They were the two of seven facilities controllers missed
  when the envelope decision was applied; wrapping them was part of finishing that job, not new work.

### 2.2 Preventive-maintenance scheduling

`serviceIntervalDays`, `lastServicedOn` and the derived `serviceDueOn` are shown on the asset
register and detail, and the dashboard counts what is overdue and due soon. There is no way to
**record** a service, so the fields can only be set at registration. `PATCH /assets/{id}` accepts
them; a maintenance screen is the natural home and that is S153 again.

### 2.3 Room and resource booking

`bookable`, `availableForBooking`, `examinationCapable` and `availableForExamination` are all
displayed, and the dashboard reports "bookable now". There is no booking. That is S159, and the
flags exist precisely so it can be built against them.

### 2.4 Floors have no screen of their own

Floors are created and read through the API and appear in the space register's context, but there is
no floor page. An estate of one building and one floor does not justify one, and a deep-linked floor
route with nothing on it would be worse than its absence. Buildings are listed on the site detail.

## 3. Known rough edges

| Where | What | Why it was left |
| --- | --- | --- |
| Space detail, blocker list | An asset-sourced blocker shows the asset's UUID in its source line (`Asset · c8f166ae-…`) | The description beside it already names the asset (`GEN-01 (GENERATOR) is OUT_OF_SERVICE`), so the identifier is redundant rather than misleading. Resolving it to a code means a second fetch per blocker. |
| Registers | Only the code cell is clickable, not the whole row | Consistent with the fleet registers, which is why it was left. It is a discoverability cost and worth revisiting across all of them at once, not in one module. |
| Site detail | No building or floor creation | The API supports both. Left with the estate-setup screens rather than added to a read-mostly page. |
| Assessment history | Fixed at the ten most recent | The full history is at `/facilities/assessments?roomId=…`, which the page links to. |

## 4. Things the browser confirmed

Recorded because each is an invariant rather than a screen, and each was exercised end to end
against a real PostgreSQL rather than a mock.

1. **A passing re-assessment closes the blocker it supersedes.** HALL-A went from 50%/Blocked to
   100%/Ready, and the FIRE-EGRESS critical blocker closed with `Superseded by assessment 84c0…`.
2. **A critical asset failure blocks the space it serves.** GEN-01 to `OUT_OF_SERVICE` put HALL-A
   into Blocked while its checklist score stayed at 100% — the score is reported beside the status
   and never drives it, which is exactly the intended behaviour and reads oddly until you know that.
3. **READY is refused while a critical blocker is open, in both layers.** The dialog disables it
   with "1 critical blocker must be resolved first."; the service independently answers
   `READINESS_BLOCKED` with "This space cannot be marked ready while 1 critical blocker(s) remain
   open." and a correlation ID.
4. **Returning the asset to service returns the space to READY** with no further action.
5. **Entitlement narrows the interface.** A `FLEET_MANAGER` opening `/facilities` gets "Facilities &
   Infrastructure is not part of your work" and no facilities sections in the sidebar; an
   `IFIMP_TECHNICIAN` sees no operating-mode control on the site page and a disabled lock button
   carrying its reason; a `FACILITIES_MANAGER` has no audit or configuration items and cannot change
   operating mode, which the matrix withholds from that role on purpose.

## 5. Two service changes the UI forced

Neither is cosmetic and both would have failed in a browser while passing every test.

- **CORS.** The facilities service allowed neither `http://localhost:8093` (the bundled dashboard's
  origin) nor `http://localhost:5005` (`npm run dev`). Every screen would have failed while every
  equivalent `curl` succeeded. It also did not expose `X-Correlation-ID`, which the client puts on
  every error it raises — so the correlation ID would have been in the service log and absent from
  the message the operator was looking at, which is the one moment it exists to be useful.
- **The envelope.** Twenty-seven fleet and eight emergency controllers return `ApiResponse<T>`; the
  facilities service's did not. The five S152 controllers were changed rather than teaching the
  shared client a per-service policy, because five outliers against thirty-five is not a difference
  worth encoding. `/actor/permissions` was changed to a flat `string[]` for the same reason. The two
  maintenance controllers were finished in this round (§2.1).

## 6. What has to happen next

1. **S153 (CMMS) screens.** The backend is built and the authorisation hole is closed; the screens
   are what remains, and they are the last thing standing between the retired static page and full
   replacement.
2. **S159 (room and resource booking)** — against the booking flags this module already surfaces.
3. **Add every new module to `SOURCES`** in `shared/layout/actorPermissions.ts`. See §1.
