# SFL operational runbooks

Written from what the code does, not from what an operations template says it should. Every command
here has been run against this platform, and every threshold quoted is the one the service actually
reads.

| Runbook | Answers |
|---|---|
| [`dead-letter-recovery.md`](dead-letter-recovery.md) | An event did not arrive. Where did it stop, and how do I replay it? |
| [`incident-response.md`](incident-response.md) | A service is down or refusing requests. What do I check, in what order? |
| [`backup-and-restore.md`](backup-and-restore.md) | How is each schema backed up, and how do I prove a restore worked? |
| [`disaster-recovery.md`](disaster-recovery.md) | The site is gone. What comes back, in what order, and what is lost? |

## The five services, and what each owns

| Service | Port | Schema | Local DB | e2e DB |
|---|---|---|---|---|
| `sfl-facilities-service` | 8091 | `facilities` | 5441 | 55441 |
| `sfl-safety-security-service` | 8094 | `safety_security` | 5442 | 55442 |
| `sfl-fleet-logistics-service` | 8093 | `fleet_logistics` | 5443 | 55443 |
| `sfl-asset-visibility-service` | 8096 | `asset_visibility` | 5444 | 55444 |
| `sfl-emergency-notification-service` | 8095 | `emergency_notification` | 5445 | 55445 |

Each service owns its schema, its migrations and its API boundary. **No runbook here may tell you to
write to another service's tables**, and none does — cross-service recovery is always through APIs and
events, because a repair that reaches across a schema boundary re-creates the coupling the
architecture exists to prevent.

## What is not covered, and must be before go-live

- **Authentication is off.** `SFL_SECURITY_ENABLED` defaults to `false` in every service, so every API
  is currently unauthenticated. None of these runbooks assumes a token, and all of them will need a
  section on credential handling once it is on. Tracked as A1 in `docs/prompts/`.
- **No vendor gateway for S174.** Emergency notification cannot actually deliver a message, so there
  is no "the SMS provider is down" procedure to write yet.
- **The SSEMP cluster does not exist.** S160, S160a, S161, S162, S162a and S163 have no service to
  operate. `sfl-safety-security-service` boots and serves nothing.
