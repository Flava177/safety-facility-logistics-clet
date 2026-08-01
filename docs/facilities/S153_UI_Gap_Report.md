# S153 CMMS UI — gap report

What the screens do not do, what driving them found, and what is deliberately absent. Companion to
[S153_UI_Screen_Inventory.md](S153_UI_Screen_Inventory.md).

## 1. What driving it found

Two defects, both invisible to a green build and 73 green tests, both found by switching actor in a
browser.

**D-01 — A technician was shown a dead Close button.** `WorkOrderDetailPage` rendered Close whenever
the order was open, disabled with whatever `closeAction` said. For a supervisor short of evidence
that is exactly right — "1 item(s) required, 0 attached" is actionable. For a technician it read
"You do not have permission for this action", on every job they would ever open, forever. No
technician holds `FACILITIES_WORK_ORDER_CLOSE`; that is the entire reason `COMPLETED` and `CLOSED`
are separate states.

The fix draws a line the rest of the module now follows:

> **A permission denial hides the control. A state or data shortfall disables it with the reason.**

The first is permanent for the session and a dead button reads as a broken screen; the second is
something the person looking at it can go and fix. `canCloseWorkOrders()` exists solely to tell the
two apart, and says so at its declaration.

**D-02 — Two empty states asserted something they could not know.** The work-order queue said
"Every work order at this site is closed or cancelled" and the fault register said "Every fault at
this site has been resolved or dismissed". Both are confident falsehoods for a contractor or a
requester, who see only their own records: the site can have a full queue they simply cannot see.
Both now describe what is visible to *you* rather than what exists.

Neither is dramatic. Both are the kind of thing that survives review and then quietly teaches a user
that the screen is unreliable.

## 2. Not built, and why

### 2.1 No file upload — but the digest is no longer typed

**Updated.** `AttachEvidenceDialog` now takes the file, and still uploads nothing.

Evidence is stored **by reference**: the bytes live in the document and object-storage service, and
this service records where they landed and what they hashed to. That is the architecture standard, it
has not changed, and it is the reason the hash is meaningful at all — if the stored object no longer
hashes to the recorded value, it changed after CLET accepted it.

What changed is **who computes the digest**. Choosing the file fills the name, the media type, the
size and the SHA-256, because the browser has the bytes and `crypto.subtle` can hash them. Before
this, a technician standing in a plant room with a photograph had to obtain a digest from somewhere
else and type sixty-four hexadecimal characters into a form. Nobody does that correctly, and a
mistyped digest is the one error in this system that surfaces years later — during an integrity
check, on evidence nobody can now re-hash — as a **false report that the file was tampered with**.

Three cases still fall back to typing, each with the reason on the field: a file over 64 MB (Web
Crypto cannot stream, so the file is read whole and the cap is what stops a mis-selected video
freezing a site laptop), a browser without Web Crypto, and a dashboard served over plain HTTP.
Somebody re-recording evidence from a paper trail has a digest and no file, and that has always been
legitimate.

**What remains a genuine gap:** the storage reference is still typed, because it is what the document
service gives back and this dashboard cannot call it. Attaching evidence is still a two-step job —
upload there, record here — and the remaining half belongs to whoever builds that integration. The
half that was error-prone is closed.

### 2.2 Editing a schedule or a vendor

Both registers create and list. `PATCH /maintenance/schedules/{id}`, `…/vendors/{id}` and both
lifecycle endpoints exist and are wired into `facilitiesApi.ts`, with no screen calling them. Left
out to keep this round to the workflow that was actually missing; a correction to a contact number
is a smaller loss than a fault register.

### 2.3 The escalation sweep has no button

`POST /maintenance/escalations/runs` is in the API layer and unused. Preventive generation got a
"Generate due work" button because a supervisor has a real reason to force it — the work appears in
their queue. Forcing an escalation only sends somebody else a notification sooner, which is not an
operator's call to make by hand.

### 2.4 Parts are removed by clicking the row

There is no delete control on a part; clicking the row removes it while the order is open. That is
wrong — a row click should open, not destroy — and it is here because the parts table has no other
per-row action to hang a button on. **Worth fixing before this is used in anger.**

### 2.5 SLA remaining is not counted down

The queue shows the deadline, and how far past it something is once `overdue` turns true. It does not
show "2h left". `minutesOverdue` comes from the service; minutes *remaining* would have to be
computed from the browser clock, and a workstation running ten minutes fast would then disagree with
the escalation sweep about what is urgent.

## 3. Things the browser confirmed

Each is an invariant rather than a screen, exercised against real PostgreSQL.

1. **A critical fault blocks its hall, and the fault page says so** — the blocking notice appears on
   the fault, not only on the space, which is the screen somebody would have to know to open.
2. **Closure is refused without evidence**, with the service's own sentence on the disabled button:
   "Required evidence must be attached before closure: 1 item(s) required, 0 attached."
3. **Attaching evidence flips the gate** to "1 attached. This work order can be closed."
4. **Closing resolves the fault and clears the blocker.** HALL-A went `BLOCKED` → `READY` and
   bookable; the fault's resolution note names the work order that closed it.
5. **A technician can complete but cannot close**, and no longer sees a dead button for it.
6. **A vendor sees only their own work.** `other.tech` got an empty queue while `a.tetteh`, the
   assignee, saw exactly one order — and a vendor's sidebar is four items, not eleven.
7. **A requester sees only their own faults**, and an empty register when they have reported none.
8. **Preventive generation is idempotent**, visible in the schedule advancing by its interval rather
   than regenerating.

## 4. Decisions worth knowing

**S153 is its own system code even though no role's entitlement differs from S152.** Recorded at the
declaration in `programmeModel.ts`. The short version: the C9 mapping treats it as a system, the
coverage claims count systems, `VITE_SFL_SYSTEMS=S153` becomes possible, and a refusal page should
name the right thing.

**`VENDOR_TECHNICIAN` holds both codes.** They need the estate *reads* — site, space, asset — because
a work order says where it is and what it is on, and dropping S152 would break the link from a job to
the hall it is in. Their sidebar is narrow because their permissions are, not because the shell
second-guesses the service.

**Nothing derived is recomputed.** `overdue`, `minutesOverdue`, `open`, `assignable`,
`dueForGeneration`, `disposalEligibleFrom` and `supportsClosure` all come down the wire. The one
piece of client-side arrangement is the queue's overdue-first sort, which reorders the service's
answer without recomputing it.

## 5. What has to happen next

1. **Document-storage upload**, so evidence can be attached without leaving the dashboard (§2.1).
2. **A delete control on parts** (§2.4) — small, and currently wrong.
3. **Edit screens for schedules and vendors** (§2.2); the endpoints are already wired.
4. **S159 room and resource booking**, which finishes IFIMP.
