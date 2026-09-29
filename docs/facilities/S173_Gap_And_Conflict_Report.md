# S173 Event Logistics & Set-Up Workflow - gap and conflict report

What is built, what is not, and what somebody has to decide before this is treated as fully
integrated. Honest by design: an unrecorded gap is one a coordinator discovers on the day of the
event.

---

## 1. S078 (CCP Events) is external and has no query API integrated with SFL

SRS-SFL-S173-01's validation rule is that "a set-up task cannot exist without a resolvable S078 event
reference." The honest question is: resolvable *against what*? S078 is CLET's own CCP Events module,
external to SFL, and the master mapping's integration note for S173 says only "tasks created from the
CCP Events system (S078) per the mapping" - it does not say S173 may call back into S078 to ask "do
you recognise this event?"

What is built (`RecordedCcpEventsDirectory`) resolves a reference two ways, both derived from what
S078 has itself told SFL through the authenticated `CCP_EVENTS` channel:

1. a reference S173 already holds a set-up task for (S078 confirmed it before), or
2. a reference arriving now as a **signed, `CONFIRMED`** hand-off - the signed confirmation is itself
   the only evidence of S078's recognition that SFL can check.

This is weaker than a real lookup. A hand-off correctly signed by an allowlisted, authenticated
`CCP-EVENTS-SIM` source but describing an event that S078 has, in truth, never confirmed (a
misconfigured integration on S078's side, not a forgery) would pass. Nothing in Phase 2's scope closes
this - it needs either a real S078 query API or a stronger hand-off contract (e.g. S078 counter-signs
a nonce SFL issued), and that is a decision for whoever owns the S078 integration, not this build.
`CcpEventsDirectoryPort` is the seam: the day S078 exposes a lookup, a real adapter replaces
`RecordedCcpEventsDirectory` behind the same interface and nothing else in S173 changes.

**Also unspecified**: S078's own status vocabulary. Nothing in the SRS or the master mapping enumerates
S078's event-status values. `S078EventStatus` (`DRAFT`, `TENTATIVE`, `CONFIRMED`, `CANCELLED`) is this
build's own reasonable set, chosen because the requirement only ever distinguishes confirmed,
not-yet-confirmed and cancelled. If S078 actually uses different literal strings, the mapping in
`EventHandoffService` (`S078EventStatus.parse`) is where to correct it - a one-line change, not a
redesign.

## 2. S172 (Catering & Cafeteria Management) is Phase 3 and not built

Explicit throughout: `event-logistics.owning-system.S172.available` is seeded `false`;
`UnbuiltCateringGateway` answers empty rather than confirming or requesting anything;
`OwningSystem.S172` is a real enum value with a real routing rule
(`EventResourceRequestService.outcomeOf`), not a special case bolted on afterwards. Every `CATERING`
resource request becomes `MANUAL_COORDINATION`, is marked as such in its `statusDetail`, appears in
the readiness view's manual-coordination list, and is never silently dropped or shown fulfilled -
S173-02's acceptance criterion, tested directly
(`ResourceRouting.catering_is_manual_coordination_not_fulfilled`). A named person can accept
responsibility for it (`acceptManualCoordination`), which is a different fact from the system
confirming delivery, and the domain rule (`ResourceRequestStatus`) makes it structurally impossible
for a manual item to become `FULFILLED` - see that enum's Javadoc for why.

The day S172 is built: implement `CateringGatewayPort` for real, flip
`event-logistics.owning-system.S172.available` to `true`, and nothing else in S173 changes.

## 3. S165 (Risk Assessment Library) is not built - every higher-risk confirmation is refused

SRS-SFL-S173-03 requires a linked, current S165 risk assessment before a higher-risk set-up task can
be confirmed, and requires the currency check to reuse S164-01's logic rather than reimplement it.
Both are done: `RiskAssessmentCurrency` (in `sfl-service-common`, shared with the future S164) is the
one and only currency rule, and S173 calls it, never reimplements it.

What S173 cannot do is ask S165 for an assessment, because S165 is in `sfl-safety-security-service`
(SSEMP), a different deployable, and is not built. S173 keeps a local projection
(`facilities.event_risk_assessments`) fed by four reserved event names
(`sfl.ssemp.risk-assessment-{published,superseded,review-lapsed,signed-off}.v1` - see
`docs/facilities/S173_Event_Contracts.md`). **Nothing publishes these events in this deployment.** The
consequence, stated plainly rather than left to be discovered: the projection is empty, every
higher-risk event's confirmation attempt is refused with `EVENT_RISK_ASSESSMENT_NOT_CURRENT` /
`NONE_LINKED`, and no amount of retrying changes that until S165 ships and starts publishing. This is
correct, fail-closed behaviour, not a defect - the alternative (confirming a major event with no risk
assessment because none could be checked) is exactly what S173-03 exists to prevent. Routine,
low-risk events are entirely unaffected, and that boundary is tested
(`RiskAssessment.routine_event_is_exempt`).

Tested against a synthetic projection fed directly through `RiskAssessmentProjectionService` (bypassing
the unbuilt event source), covering: no assessment linked, a linked-but-lapsed one, a linked-but-
superseded one, and a higher-risk assessment signed off only by its own author - all four refuse, each
naming its reason; a current, independently signed-off assessment lets confirmation through.

## 4. Security staffing: the SRS routes it to S159, but it actually lives in SSEMP

SRS-SFL-S173-02: "Resource requests are raised against Room & Resource Booking (S159) for the
venue/AV/setup tasks" - and the requirement summary lists `SECURITY` among S173's typed resource
categories alongside staging, AV, catering and signage, routed the same way. This build follows that
literally: `EventResourceType.SECURITY` routes to `OwningSystem.S159` and is fulfilled as a named,
bookable S159 resource (a "security team" or "security post" resource row), exactly like AV or
staging.

In the Phase 1 platform, however, security staffing and rostering is an SSEMP concern (the SOC,
`SECURITY_DIRECTOR`/`SOC_OPERATOR` roles), not something S159's room-and-resource model was designed
to represent. S159 can hold a bookable resource named "Security Post - Main Gate," but it cannot
express a rostered shift, a named officer, or an SSEMP-side scheduling constraint - it is a diary
entry, not a duty roster. Two consequences, flagged rather than resolved:

- A site that wants real security **staffing** (who, which shift, with what competency) for an event
  needs an SSEMP-side system this SRS does not name for S173, and S173 has no port to one.
- What S173 can honestly offer today is "a security resource was requested and either booked or
  conflicted" - useful for the coordinator's readiness view, not a substitute for SSEMP rostering.

No code change is proposed here: the SRS is explicit, and building an SSEMP integration S173's spec
does not ask for would be scope invention. This is recorded so a reviewer approving S173 as "handles
event security" understands exactly what that claim covers.

## 5. Owning-system availability registry, and what "unavailable" means for the two built-but-switched-off cases

`event-logistics.owning-system.{S159,S153,S169}.available` all default `true` - the systems are
built and in this deployable (S159, S153) or reachable through a stable contract (S169). Switching one
off routes its resource type to `MANUAL_COORDINATION` the same way S172 is handled, which is
deliberately the same code path: an operator disabling an integration (a maintenance window on S169,
say) gets the same safe degradation as a system that was never built, rather than a different failure
mode to learn.

## 6. S169 (Cleaning & Janitorial Schedule Management) is being built in a different worktree

At the time this module was built, S169's `EventCleaningCapacity` contract interface exists but its
real implementation does not - the placeholder bean (`UnbuiltEventCleaningCapacity`) throws
`IllegalStateException`. `S169CleaningCapacityGateway` catches exactly that (not any wider exception)
and answers `unavailable`, so a `CLEANING` request becomes `MANUAL_COORDINATION` until the S169 build
lands in this repository. Once it does, no change is needed here: the real `EventCleaningCapacity`
bean replaces the placeholder by Spring wiring, and `S169CleaningCapacityGateway.reserve` starts
receiving real reservations and real conflicts instead of the caught exception.

This module's own tests use a double of `CleaningCapacityPort` (S173's own port), per the build
instructions, rather than depending on S169's real implementation - so S173's test suite is
independent of when S169 lands.

## 7. What is deliberately not built

- **No endpoint creates a set-up task directly.** SRS-SFL-S173-01's validation rule ("no set-up task
  may exist without a resolvable S078 event reference") is held structurally: `EventSetupTask`'s only
  public factory (`fromHandoff`) requires the reference, its only caller is `EventHandoffService`
  after resolution, `s078_event_reference` is `NOT NULL` and unique in V20, and
  `EventLogisticsArchitectureTest` holds the module's dependency boundaries so nothing outside the
  hand-off path can reach it.
- **No manual trigger for the escalation or synchronisation sweeps.** Both run on a timer; a
  shorter interval in a test/dev environment (`sfl.event-logistics.*.interval-ms`) is the supported
  way to see them run sooner - see the runbook.
- **No notification delivery.** As with every other IFIMP module, S173 records notification intent
  (`notifiedTo`, `RECORDED`) rather than claiming a delivery nothing here can make.

## 8. New audit actions and error codes

All fourteen `AuditAction` constants and all four `FacilitiesErrorCode` constants this module needs
were already pre-seeded in the foundation commit's S173 block; none were appended. No new permission,
role or shared-file change was required or made.
