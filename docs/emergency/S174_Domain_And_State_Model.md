# S174 Domain and State Model

Package `gh.edu.clet.sfl.emergencynotification.domain`. Aggregates are immutable Java records with explicit
transition methods returning copies (the SFL house style). Value objects `RecordMetadata`, `SiteCode`,
`SourceChannel` are defined in this service's domain (not shared) so the domain stays framework-free.
Persistence owns the optimistic-lock version. All cross-module references are held by ID only.

## Aggregates

| Aggregate | Identity / invariant |
|---|---|
| `NotificationTemplate` | `(siteCode, templateCode)` unique among active; channel set + body; `breakGlassEligible` flag; lifecycle ACTIVE/INACTIVE/SUSPENDED/ARCHIVED |
| `EmergencyScenario` | `(siteCode, scenarioCode)` unique; priority/severity, default template + audiences; break-glass eligibility |
| `AudienceGroup` | `(siteCode, groupCode)` unique; recipient references by ID; masked contact fields |
| `RecipientZone` | `(siteCode, zoneCode)` unique; building/room/zone reference |
| `NotificationActivation` | `(siteCode, activationNumber)` unique; the workflow aggregate; carries scenario/template/audiences/zones/channels, mode (ROUTINE/BREAK_GLASS/DEGRADED), status, approval + after-action fields, delivery/ack summary, closure reason/evidence |
| `NotificationChannel` | One per activation×channel type; per-channel status + counts (queued/sent/delivered/failed/acknowledged) |
| `NotificationSendEvent` | Append-only fan-out record per activation×channel (outbox correlation) |
| `DeliveryReceipt` | `(activationId, provider, providerMessageId)` unique (idempotent callback); status DELIVERED/FAILED/… |
| `Acknowledgement` | `(activationId, recipientRef)` unique (idempotent); acknowledged time + channel |
| `DrillRun` | A rehearsal activation with performance metrics; never a real broadcast |

## Enumerations

- `ActivationMode`: `ROUTINE`, `BREAK_GLASS`, `DEGRADED`
- `ActivationStatus`: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `ACTIVATING`, `ACTIVE`,
  `BREAK_GLASS_ACTIVE`, `PARTIALLY_DELIVERED`, `ESCALATED`, `ALL_CLEAR_PENDING`, `CLOSED`, `CANCELLED`,
  `FAILED`, `REOPENED`
- `ChannelType`: `SMS`, `EMAIL`, `PUSH`, `VOICE`, `SIREN`, `DIGITAL_SIGNAGE`
- `ChannelStatus`: `PENDING`, `SENDING`, `DELIVERED`, `PARTIALLY_DELIVERED`, `FAILED`
- `DeliveryStatus`: `QUEUED`, `SENT`, `DELIVERED`, `FAILED`, `EXPIRED`
- `Priority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `RecordLifecycle`: `ACTIVE`, `INACTIVE`, `SUSPENDED`, `ARCHIVED`
- `RetentionClass`: `OPERATIONAL_1_YEAR`, `COMPLIANCE_7_YEARS`, `INCIDENT_10_YEARS`, `LEGAL_HOLD`

## Activation state machine

**Routine path:**
`DRAFT → PENDING_APPROVAL → APPROVED → ACTIVATING → ACTIVE → ALL_CLEAR_PENDING → CLOSED`

**Break-glass path (declared emergency, authorised role + break-glass-eligible template):**
`DRAFT → BREAK_GLASS_ACTIVE (send immediately, no pre-approval) → ACTIVE/PARTIALLY_DELIVERED →
ALL_CLEAR_PENDING → CLOSED`. Closure is **blocked** until after-the-fact approval + justification are
recorded (`EMERGENCY_AFTER_ACTION_APPROVE`).

**Branches:** `REJECTED` (routine approval denied), `CANCELLED` (from DRAFT/PENDING_APPROVAL/APPROVED),
`FAILED` (all channels failed), `PARTIALLY_DELIVERED` (some channels/recipients failed), `ESCALATED`
(failed/unacknowledged recipients past SLA), `REOPENED` (authorised reopen of a CLOSED activation).

**Invariants enforced by `NotificationActivation`, `BreakGlassPolicy` and `ActivationService`:**

- Routine activation cannot `activate` before `APPROVED`.
- Break-glass requires an authorised `EMERGENCY_BREAK_GLASS_SEND` actor and a break-glass-eligible
  template/scenario; it is never gated by routine approval.
- `all-clear` is only valid from an active state (`ACTIVE`, `BREAK_GLASS_ACTIVE`, `PARTIALLY_DELIVERED`,
  `ESCALATED`).
- **Closure requires** closure reason **and** a delivery/acknowledgement summary **and** required
  evidence reference(s); for break-glass, after-the-fact approval must already be recorded.
- Delivery-status and acknowledgement callbacks are idempotent by their unique keys; a replay updates
  counts once and never double-applies.

## Life-safety invariants (Arch §0E)

SFL never actuates certified life-safety hardware. The domain models activation, audience, channel,
delivery and acknowledgement facts only; fire/intrusion feeds arrive observe-only via `LifeSafetyEventPort`
and never drive actuation. The fast-lane and degraded-mode paths are represented as activation
`mode`/metadata and measured with recorded adapters; they change *timing/route*, never the certified
actuation authority.
