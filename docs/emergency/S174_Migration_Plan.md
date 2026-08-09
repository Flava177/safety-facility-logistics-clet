# S174 Migration Plan

Schema `emergency_notification`. Flyway migrations `V1`-`V8` live under
`services/sfl-emergency-notification-service/src/main/resources/db/migration`. No cross-schema foreign keys are
used; external modules such as incidents, sites and recipients are referenced by ID only.

| Migration | Tables / content |
|---|---|
| V1 `V1__service_foundation.sql` | `CREATE SCHEMA emergency_notification`; `service_metadata`; `outbox_messages`; `integration_inbox_messages`; append-only `audit_events` hash chain |
| V2 `V2__emergency_templates_and_scenarios.sql` | `notification_templates` and `emergency_scenarios`, with site-scoped active identifiers and break-glass eligibility flags |
| V3 `V3__emergency_audience_and_zones.sql` | `audience_groups` and `recipient_zones`, with site scope and masked/contact reference metadata |
| V4 `V4__emergency_activations.sql` | `notification_activations` and `activation_history`, including routine/break-glass/degraded workflow fields |
| V5 `V5__emergency_delivery_and_acknowledgements.sql` | `notification_channels`, `notification_send_events`, `delivery_receipts` and `acknowledgements` with callback idempotency keys |
| V6 `V6__emergency_evidence_audit_and_dashboard.sql` | `evidence_references`, `dashboard_snapshots` and `drill_runs` |
| V7 `V7__emergency_runtime_config_and_defaults.sql` | `runtime_configuration` plus seeded SLA, freshness, scheduling and simulator integration defaults |
| V8 `V8__emergency_command_idempotency.sql` | `command_idempotency_keys` for retried activation creation and break-glass creation requests keyed by `(operation, idempotency_key)` |

## Standard Column Contract

Operational tables carry `id`, `site_code`, status/lifecycle where applicable, created/modified metadata,
`source_channel`, `correlation_id` and optimistic `version` where records are mutable. Indexes cover site/date,
status/SLA queues, activation lookup and idempotency constraints.

## Runtime Defaults

V7 seeds platform defaults for acknowledgement SLA, dashboard freshness, fast-lane target, scheduling toggles and
simulator callback allowlist/secret placeholders. Site-specific overrides use the same `runtime_configuration`
table.
