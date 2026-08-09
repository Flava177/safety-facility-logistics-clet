# S168 — Fuel fraud control framework

**Status:** design, with an implementation register. Written 8 August 2026.

This is the control design for card-based fuel purchasing by field drivers, and an honest statement
of which parts of it the platform already enforces, which parts are specified but not built, and
which parts cannot be built by us alone.

Read `S168_Fuel_Gap_And_Conflict_Report.md` alongside it. Where the two disagree, the gap report is
the older document and this one is the correction.

---

## 1. The threat model, stated plainly

A driver holds a card loaded with an allocation, usable at approved providers (GOIL, TotalEnergies,
Shell, Allied Oil, Star Oil). Limits are per driver — for example GHS 100 per transaction, GHS 200
per day, GHS 2,000 per month. After fuelling, the driver submits the pump receipt and a photograph
for reconciliation.

Five attacks, in rough order of how often they succeed:

| # | Attack | What defeats it |
|---|--------|-----------------|
| A1 | **Collusion with the attendant.** Swipe GHS 200, take GHS 120 of fuel and GHS 80 in cash. | Volume-vs-money arithmetic, consumption band, provider feed reconciliation |
| A2 | **Fuelling a private vehicle.** | Card-to-vehicle binding, odometer continuity, geofence, pump photograph |
| A3 | **Recycled evidence.** One photograph, four claims. | Perceptual + cryptographic hashing of submitted images |
| A4 | **Forged receipt.** | OCR cross-check against the card transaction; provider feed |
| A5 | **Photographing someone else's pump.** | EXIF time and GPS, in-app capture, geofence |

The evidence layer is the weak point precisely because A3, A4 and A5 all attack it, and all three are
cheap to attempt.

---

## 2. The single most important design decision, already taken

**The claimant does not choose the volume.**

`CaptureTransactionDialog` asks for the *amount paid* and reads the *price per litre* from the
platform's posted-price register. Litres is derived, not typed:

```
litres = amountPaid ÷ postedUnitPrice
```

This is what turns A1 from an accounting problem into a physics problem. Inflating the money now
inflates the volume, and volume runs into tank capacity, the consumption band and the daily and
monthly volume ceilings — every one of which the service already checks. Before the inversion, an
overstated amount touched none of them.

Where no posted price is on file the field opens up and the form says, in as many words, that nothing
is checking it. **Recording posted prices per provider per site is therefore a control, not
housekeeping**, and it is the cheapest one on this page.

---

## 3. What the platform enforces today

Twenty-one reconciliation rules, each recorded per transaction with the policy version that produced
it. Verified against `FuelApplicationService.reconcile` and `FuelAnomalyCase.Type`.

### 3.1 Preventive — card level

| Control | State | Where |
|---|---|---|
| Card must be in the register and live | **Built** | `CARD_KNOWN` → `CARD_UNKNOWN` |
| Card bound to a vehicle; mismatch flagged | **Built** | `CARD_VEHICLE_MATCH` → `CARD_VEHICLE_MISMATCH` |
| Per-transaction money cap | **Built** | `CARD_TRANSACTION_LIMIT`, compares `totalCost` |
| Daily money cap | **Built** | `CARD_DAILY_LIMIT` |
| Monthly money cap | **Built** | `CARD_MONTHLY_LIMIT` |
| Approved provider list | **Built** | `APPROVED_VENDOR`; the capture form offers only these |
| Approved fuel product | **Built** | `FUEL_PRODUCT` |
| Card suspension / cancellation | **Built** | `FuelCard.Status` |

**Note on where money limits live.** Per-driver monetary caps are a property of the **card**, not of
the site policy. The site policy's `maxPerTransaction`, `dailyLimit` and `monthlyLimit` are
**quantities in litres**. Both are deliberate and they are checked against different sums
(`sumCost` vs `sumQuantity`), but it is not obvious from the field names, and the GHS 100 / 200 /
2,000 example in the brief is configured on the card.

### 3.2 Evidence integrity

| Control | State | Where |
|---|---|---|
| Receipt required, with a grace period | **Built** | `RECEIPT` → `MISSING_RECEIPT` |
| Pump-meter photograph required | **Built** | `MISSING_PUMP_IMAGE` |
| Files uploaded in-form, never an identifier to paste | **Built** | `evidenceFilesApi.upload` |
| Content sniffed by magic bytes, not by file name | **Built** | `UploadedFileScanner` |
| Active content in PDFs refused | **Built** | `UploadedFileScanner` |
| Size capped at 5 MB, both ends | **Built** | `UploadedFileScanner.MAX_BYTES` |
| SHA-256 computed by the service from the bytes it received | **Built** | never trusted from the client |
| **Exact-duplicate image detection** | **Built** | `EVIDENCE_REUSED`, on the digest |
| Camera hint on the pump photograph | **Built, weak** | `capture="environment"` — a hint, not a control |
| Perceptual hashing (re-photographed, cropped, recompressed) | **NOT BUILT** | §5.1 |
| EXIF timestamp / GPS validation | **NOT BUILT** | §5.2 |
| Gallery upload blocked | **NOT BUILT** | §5.1 |
| Receipt OCR cross-check | **NOT BUILT** | §5.3 |

### 3.3 Reconciliation and anomaly detection

| Control | State | Where |
|---|---|---|
| Volume over tank capacity | **Built** | `TANK_CAPACITY` |
| Litres/km outside the vehicle's band | **Built** | `CONSUMPTION_RANGE` → `ABNORMAL_CONSUMPTION` |
| Odometer regression | **Built** | `ODOMETER_NON_REGRESSION` |
| Implausible odometer jump | **Built** | `ODOMETER_JUMP` |
| Price not what the provider posted | **Built** | `PRICE_DEVIATION` |
| Price swing against the previous fill | **Built** | `COST_VARIANCE` |
| Repeat transactions in a window | **Built** | `UNUSUAL_PATTERN`, windowed and thresholded |
| Fuelling outside an assigned trip | **Built** | `TRIP_MATCH` → `OUTSIDE_TRIP` |
| Driver eligibility, vehicle availability | **Built** | `DRIVER_ELIGIBLE`, `VEHICLE_OPERATIONAL` |
| Logbook agreement | **Built** | `LOGBOOK_MISMATCH`, `MISSING_LOGBOOK` |
| **Provider transaction feed reconciliation** | **PARTIAL** | ingest endpoint exists; no provider is connected |
| Refills at impossible intervals | **NOT BUILT** | §5.4 |
| Off-route fuelling (geofence) | **NOT BUILT** | §5.5 |
| Month-end spending spike | **NOT BUILT** | §5.6 |

### 3.4 Governance

| Control | State | Where |
|---|---|---|
| Anomaly case workflow with SLA and escalation | **Built** | 12-state `FuelAnomalyCase.Status` |
| Closure requires explanation, decision **and** evidence | **Built** | `FuelAnomalyCase.close` |
| Separation of duties on evidence export | **Built** | request and approval are different actors |
| Hash-chained, append-only audit | **Built** | `JpaAuditAdapter` |
| Supervisor dashboard | **Built** | `FuelDashboardPage`, `FuelAnomaliesPage` |
| Random physical audit sampling | **NOT BUILT** | §5.7 |
| Sanctions register | **NOT BUILT** | out of scope for the platform; HR process |

---

## 4. Priority — impact against effort

Impact is "how much of A1–A5 does this close". Effort is engineering weeks, one developer.

### Do first — high impact, low effort

| Control | Impact | Effort | Provider needed? |
|---|---|---|---|
| **Populate the posted-price register for every provider and site** | Very high — §2 is inert without it | Days, and it is data entry | No |
| **Set per-card money limits for every issued card** | High — A1 ceiling | Days, data entry | No |
| **Bind every card to a vehicle** | High — A2 | Days, data entry | No |
| **Perceptual hashing (§5.1)** | High — closes A3 properly | ~1 week | No |
| **Month-end spike detection (§5.6)** | Medium | ~3 days | No |

The first three are configuration, not code. They are listed first because the framework is already
built around them and they are currently the difference between rules that fire and rules that pass
vacuously.

### Do next — high impact, real effort

| Control | Impact | Effort | Provider needed? |
|---|---|---|---|
| **Provider transaction feed (§5.8)** | **Highest of all** — independently closes A1, A3, A4 | 2–4 weeks per provider | **Yes** |
| **EXIF time and GPS validation (§5.2)** | High — A5 | ~1.5 weeks | No |
| **Geofence at approved stations (§5.5)** | High — A2, A5 | ~2 weeks | No (needs station coordinates) |
| **In-app camera enforcement (§5.1)** | Medium-high — A3, A5 | ~2 weeks, needs a mobile client | No |

### Do later — lower impact or heavy effort

| Control | Impact | Effort | Provider needed? |
|---|---|---|---|
| Receipt OCR (§5.3) | Medium — mostly duplicates the provider feed | 3–4 weeks | No |
| Impossible-interval detection (§5.4) | Medium | ~1 week | No |
| PIN binding at the pump | High | — | **Yes**, provider-issued |
| Time-of-day card rules | Low-medium | ~1 week | Partly |
| Random physical audit sampling (§5.7) | Medium | ~1 week | No |

---

## 5. Specifications for what is not built

### 5.1 Perceptual hashing and capture provenance

SHA-256 catches the same file submitted twice. It does not catch a receipt photographed a second
time, an image cropped by a pixel, or one re-saved at a different quality — which is the whole of A3
once anybody notices the exact-duplicate rule exists.

**Design.** Compute a difference hash (dHash, 64-bit) on upload, store it beside the SHA-256, and
compare new submissions against the site's recent window by Hamming distance. Distance ≤ 10 is a
near-duplicate and raises `EVIDENCE_REUSED` with the distance and the prior evidence id in the
detail map. dHash is chosen over pHash because it is a dozen lines, has no DCT dependency, and is
robust to exactly the transformations listed above.

Requires a new column on the evidence content table and an index strategy for the window search
(BK-tree, or brute force over a 30-day window per site — at this volume brute force is fine).

**In-app camera only** is a mobile-client change, not a web one. `capture="environment"` is a hint
that desktop browsers ignore and determined users bypass; it is documented as a hint in
`EvidenceFileField` and must not be described as a control.

### 5.2 EXIF validation

Read `DateTimeOriginal` and `GPSLatitude`/`GPSLongitude` from the JPEG on upload.

- Photograph taken more than *N* minutes from the transaction time → anomaly.
- GPS more than *R* metres from the station → anomaly (see §5.5).
- **EXIF absent entirely** → its own anomaly type. Stripped EXIF is a signal, not a neutral state,
  and it must not be treated as a pass. Note that several messaging apps strip EXIF, so this will
  have a real false-positive rate until in-app capture (§5.1) exists.

### 5.3 Receipt OCR

Extract litres, unit price, total, station and timestamp from the receipt image and compare against
the submitted transaction. Any field disagreeing beyond tolerance raises an anomaly.

Deprioritised deliberately: where a provider feed exists (§5.8) it answers the same question
authoritatively, and OCR on a thermal receipt photographed at night is not a reliable witness.

### 5.4 Impossible refill intervals

Two fills for the same vehicle closer together than the tank could plausibly consume, given the
distance travelled between them. The inputs are already stored — `findPreviousTransaction` supplies
the previous fill, and the odometer delta is already computed for `CONSUMPTION_RANGE`. This is a new
rule over existing data, which is why it is cheap.

### 5.5 Geofence

Requires a station register with coordinates. The capture form already resolves the station through
Google Places, so a coordinate is available at capture time and is currently discarded — capturing it
is a small change and is the prerequisite for both this and §5.2.

Rule: transaction station coordinate more than *R* metres from the registered station, or the
photograph's GPS more than *R* metres from either. Suggested *R* = 250 m.

### 5.6 Month-end spending spike

Per driver, compare the last five days of the month against that driver's trailing three-month
average for the same window. Beyond a configurable multiple, raise `UNUSUAL_PATTERN`. Catches
allocation burn-down, which none of the point-in-time limits can see because every individual
transaction is within its cap.

### 5.7 Random physical audit sampling

Select *n*% of reconciled transactions per period at random, weighted towards drivers with prior
anomalies, and raise a review case that requires a physical check. The value is deterrent, and the
deterrent only works if the selection is visibly random and the sample is actually worked.

### 5.8 Provider transaction feed — **the highest-value control on this page**

Every control above judges what the *driver* submitted. A provider feed judges it against what the
*pump* recorded, and it is the only control here that is independent of the claimant.

The platform is already shaped for it: `FuelTransaction` carries `providerTransactionId` and
`sourceSystem`, capture is idempotent on the provider reference, and `FuelIntegrationPage` exists.
What is missing is a connected provider.

**What to ask each provider for**, in descending order of value:

1. A transaction feed — card, station, timestamp, litres, unit price, total, pump ID.
2. Real-time authorisation hooks — merchant lock, per-card caps enforced *at the pump* rather than
   detected afterwards.
3. PIN or vehicle-tag binding at the pump.
4. Time-of-day and station restrictions on the card itself.

Items 2–4 move controls from *detective* to *preventive*, which is the single biggest available
improvement to this framework and is entirely outside our control. Item 1 alone would close A1, A3
and A4.

---

## 6. What is enforceable by us alone

Everything in §3 marked **Built**, plus §5.1, §5.2, §5.4, §5.5, §5.6 and §5.7. That is a substantial
control framework that needs no provider cooperation whatever.

What we cannot do alone: stop a transaction happening. Every control we own is detective — it judges
a purchase that has already completed. Preventive control at the pump requires the card issuer, and
should be a procurement requirement in the next fuel-card contract rather than an engineering task.

---

## 7. Immediate recommendations

1. **Load the posted-price register.** Without it the volume derivation in §2 — the control the whole
   design rests on — falls back to a price the claimant types, and the form says so.
2. **Set money limits and vehicle bindings on every issued card.** The rules exist and pass
   vacuously when the fields are null.
3. **Build perceptual hashing.** One week, closes the cheapest attack properly.
4. **Open the provider conversation now.** It is the highest-value item and the longest lead time,
   and items 2–4 of §5.8 belong in a contract, not a backlog.
