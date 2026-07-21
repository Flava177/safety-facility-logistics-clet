# SFL Platform — Demo Cheat-Sheet (S153 Reference Slice)

> Audience: mixed (business + technical). Keep the story on **governance, audit, and reuse**, drop into technical detail only when asked. Total target: 10–15 min.

---

## 0. One-line pitch (say this first)

> "SFL is one governed platform for Safety, Facilities and Logistics — instead of 13 disconnected apps. Today I'll show the reference module: the maintenance system, S153. It's the blueprint every other system will copy for how we store data, record an audit trail, and publish events."

---

## 1. The problem & the strategy (≈2 min, no screen needed)

**The problem:** 13 fast-track systems across Safety, Facilities & Logistics. The risk is building 13 islands that don't talk to each other, or rebuilding specialist hardware (CCTV, fire panels, fuel pumps) we should just buy.

**Our decision model** — every system is classified as one of three:

| Decision | Meaning | Example |
|---|---|---|
| **Build** | SFL owns the workflow, rules, data, audit | Maintenance (S153), Incident reporting |
| **Buy & Integrate** | Vendor owns the device; SFL reads its data | CCTV, access control, fire panels |
| **Hybrid** | Vendor captures device data; SFL owns the workflow & governance | Fleet, fuel, visitor management |

**The golden rule:** a business module never talks directly to a vendor, RabbitMQ, Redis, Keycloak or the database. It goes through a **port (interface) → adapter**. That keeps the platform swappable and testable.

> Say: "This classification is what stops the project sprawling. S153 is our first **Build** — the one we prove the pattern on."

---

## 2. Architecture in one breath (≈2 min)

Four domain platforms group the 13 systems:

- **IFIMP** — Infrastructure & Facilities (maintenance, CAFM, room booking) ← *S153 lives here*
- **SSEMP** — Safety & Security (visitor, CCTV, alarms, fire, incidents, mass notification)
- **FTLMP** — Fleet, Transport & Logistics (fleet, fuel, mailroom)
- **AVAMP** — shared Asset & Device reference layer for all of them

**Clean Architecture — four layers, dependencies point inward:**

```
Domain  →  Application  →  Infrastructure adapters  →  Outside world
(rules)    (use cases /     (PostgreSQL, RabbitMQ,      (DB, broker,
            ports)           audit, workflow)            vendors)
```

- **Domain** = the business rules (a fault, a work order, what's a valid state change). No database, no framework.
- **Application** = the use cases, defined against **ports** (interfaces like `IFacilityFaultRepository`, `IOutboxStore`, `IAuditWriter`).
- **Infrastructure** = the real implementations (EF Core + PostgreSQL, the outbox table, the audit writer).
- We can swap in-memory for PostgreSQL, or placeholder-for-real RabbitMQ, **without touching the business logic.**

> Say: "The business code doesn't know it's talking to PostgreSQL. That's the whole point — every other system reuses these same seams."

---

## 3. Live demo — the click path (≈5 min)

> Open on the **login page** (it starts here by design — login-first).

**Step 1 — Sign in**
- Use the seeded credential `admin@sfl.local` / `Sfl@2026`.
- Say: *"Keycloak-ready security; for the demo it's a seeded login. Notice it routes me into the dashboard only after auth."*

**Step 2 — Dashboard**
- Point at the live metric cards (open faults, work orders, outbox events).
- Say: *"This is the consolidated operational view — the thing you don't get with 13 separate apps. These numbers are driven by the records I create live, not mock data."*

**Step 3 — Report a facility fault** (`Facility Faults`)
- Fill the **blank** form (site, location, title are required — note the red asterisks and validation).
- Submit → a toast confirms, a fault reference appears in the list.
- Say: *"One submit just did three things in a single transaction — created the fault record, wrote an audit record, and queued an integration event. Watch."*

**Step 4 — Create a work order from the fault**
- Click **Work order** on the fault row → toast + it appears under **Work Orders**.
- On Work Orders: **assign an owner**, then **close** it.
- Say: *"The work order is created from the fault — it links back to it. Assignment and closure each update state and leave their own trail."*

**Step 5 — Show the governance payoff** (`Audit and Outbox`)
- Show the **Audit Records** (every action stored) and **Outbox Messages** with the real event names:
  - `sfl.ifimp.facility-fault-reported.v1`
  - `sfl.ifimp.work-order-created.v1`
- Say: *"Every business action produced a tamper-evident audit record and a versioned event ready to broadcast to other systems. This is how modules stay decoupled but connected."*

---

## 4. What just happened under the hood (≈2 min — for the technical questions)

When you reported that fault, the application service did this **atomically**:

1. **Domain** validated and created the `FacilityFault` aggregate (`FacilityFault.Report(...)` returns a `Result` — no exceptions for business rules).
2. Saved the fault via the **repository port**.
3. Wrote an **AuditEvent** — actor, source (`SFL.IFIMP.S153`), correlation ID, and a **SHA-256 hash of the payload** (tamper-evident, chainable via previous-hash).
4. Wrote an **OutboxMessage** (status `Pending`, versioned).
5. **One `SaveChanges`** committed business data + audit + outbox **together** — the **transactional outbox pattern**. Either everything lands or nothing does.

Then separately, a **Worker Service** polls the outbox for pending rows and publishes them through `IIntegrationEventBus` (RabbitMQ-ready) — so publishing can't lose events or block the user.

> Say: "No dual-write problem. The event and the data commit in the same transaction; delivery happens reliably afterwards."

**Persistence layout (PostgreSQL, schema-per-module):**

| Schema.table | Holds |
|---|---|
| `ifimp.facility_faults` | Fault records |
| `ifimp.work_orders` | Work orders |
| `platform.audit_events` | Audit trail |
| `messaging.outbox_messages` | Outbound events |

---

## 5. Quality & testing (mention briefly)

- Seven test projects: **Unit, Integration, API, Architecture, Contract, End-to-End, Solution.**
- **Architecture tests** enforce the golden rule in CI — e.g. a domain module *cannot* reference a vendor/DB client. The rules are guarded, not just documented.
- An EF model test validates the table/schema mapping **without** needing a live database.

---

## 6. Where it's going (close on this)

> "S153 is the proven template. Rolling out the remaining systems is now mostly repetition of this pattern — same persistence, same audit, same outbox, same event catalog. Next concrete step is wiring the real RabbitMQ publisher to the approved team pattern; the worker is already built to drop it in."

---

## Likely questions — quick answers

- **"Is it tied to PostgreSQL / RabbitMQ?"** No — both sit behind ports. In-memory today, PostgreSQL and RabbitMQ adapters swap in with no domain changes.
- **"How do we know the audit can't be tampered with?"** Each audit record stores a SHA-256 payload hash and supports hash-chaining to the previous record.
- **"What if event publishing fails?"** The event is already safely committed to the outbox; the worker retries until delivered. Nothing is lost.
- **"Why not buy a CMMS?"** S153 is classified **Build** because maintenance is workflow-, approval- and audit-heavy and unique to us — but it's adapter-ready if a vendor becomes the source of record later.
- **"How is security handled?"** Keycloak-ready; the portal is login-first with auth-gated routes. The demo uses seeded credentials.

---

## 30-second elevator version (if you're short on time)

> "One governed platform, not 13 apps. Each system is classified Build, Buy, or Hybrid so we never rebuild what we should buy. S153 — maintenance — is our reference Build: report a fault, it spins up a work order, and every action automatically writes a tamper-evident audit record and a reliable integration event. Clean Architecture means the business rules don't care whether the storage is PostgreSQL or the broker is RabbitMQ — so every remaining system just reuses this same proven pattern."
