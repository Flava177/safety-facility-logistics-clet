# S157 — Utility metering / AMI gateway / vendor billing feed: procurement-gate evidence

- Delivery classification: **Hybrid** (`phase-2-system-classification.md`)
- Integration key: `energy-metering` · adapter shipped: `SimulatedMeteringAdapter` (simulator) · inbox channel: `ENERGY_METERING`
- Evidence reference variable: `SFL_S157_PROCUREMENT_GATE_EVIDENCE` — **unset**
- Current gate status: **SIMULATED_ADAPTER_ONLY — not integrated**

No vendor has been selected. Every row below is open. Complete them against the selected product,
file the evidence pack, and only then set `SFL_S157_PROCUREMENT_GATE_EVIDENCE` to that pack's reference.

| # | SRS §5.2 / checklist requirement | Evidence required | Status |
|---|---|---|---|
| 1 | Published, versioned interface (API / webhook / export) | Vendor API documentation and version policy | Open |
| 2 | Authenticated inbound messages | Confirmation the vendor (or its gateway) can sign each message with HMAC-SHA256 over `signedAt + "." + body`, or present a client certificate for mutual TLS at S217a | Open |
| 3 | Source-allowlisted | One source id per site gateway, registered under `sfl.facilities.vendor-inbox.sources` with that site only (never `*`) | Open |
| 4 | Schema-validated | Sample payloads for every message type, mapped onto the SFL reading model by a dedicated adapter | Open |
| 5 | No vendor credential outside the platform secret store | The per-source secret held in the platform secret store and injected by environment; the shipped `dev-only-not-a-real-secret` replaced | Open |
| 6 | Test / sandbox environment | Sandbox endpoint and credentials for contract testing before production connection | Open |
| 7 | Device health / offline reporting | How the product reports a sensor or gateway going silent (the service also detects staleness itself) | Open |
| 8 | Rate limits and retry semantics | Documented limits; confirmation the vendor retries with the same idempotency key | Open |
| 9 | Data ownership and export | Contractual right to retain telemetry per the SRS §4.2 retention policy | Open |
| 10 | Support and change notice | Integration support channel and notice period for interface changes | Open |

Until this page is complete the runbook's statement stands: the feed is simulated, and no status
report may describe S157 as integrated.
