# sfl-fleet-logistics-service

Fleet and logistics service for S166, S168_fuel and S171.

Boundary rules:
- Own this service's database schema only: `fleet_logistics`.
- Publish cross-service changes through the service outbox.
- Consume external events idempotently through the service inbox.
- Store vendor payloads through adapters; do not leak vendor models into domain packages.
- Store evidence file references and hashes only; do not store large files or CCTV video in this database.

## S166 review entry points

- Final implementation report: `docs/fleet/S166_Final_Implementation_Report.md`
- Requirement traceability matrix: `docs/fleet/S166_Requirement_Traceability_Matrix.md`
- API inventory: `docs/fleet/S166_API_Inventory.md`
- Test plan: `docs/fleet/S166_Test_Plan.md`
- Operational console: `/fleet/index.html`

## Local verification

Run from the `services` reactor root:

```powershell
mvn -pl sfl-fleet-logistics-service -am test -q
```

The Postgres end-to-end test uses Testcontainers and is annotated with
`@Testcontainers(disabledWithoutDocker = true)`, so it is skipped on machines without Docker and runs automatically
where Docker is available.

## Fleet schedulers

| Scheduler | Property | Default |
|---|---|---|
| Outbox drain | `SFL_FLEET_OUTBOX_SCHEDULER` | `true` |
| SLA evaluation | `SFL_FLEET_SLA_SCHEDULER` | `true` |
| Compliance/service sweep | `SFL_FLEET_COMPLIANCE_SCHEDULER` | `true` |
| Compliance/service sweep cron | `SFL_FLEET_COMPLIANCE_CRON` | `0 5 1 * * *` |
| Dashboard refresh | `SFL_FLEET_DASHBOARD_SCHEDULER` | `true` |

