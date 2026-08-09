# S168_fuel Test Plan

## Quality layers

- Pure domain tests for money/quantity, logbook and anomaly transitions, policy evaluation and odometer rules.
- Application tests for authorization, site scope, idempotency, audit/outbox atomicity and closure gates.
- MVC/API tests for validation, envelopes, pagination, workflow endpoints and OpenAPI exposure.
- PostgreSQL/Testcontainers tests for Flyway, constraints, optimistic locking, imports and projections.
- ArchUnit rules prevent domain dependencies on Spring/JPA/HTTP/vendor libraries.
- E2E coverage includes the 17 mandatory scenarios in the implementation brief, especially CT-08.

## Regression gate

`mvn -pl sfl-fleet-logistics-service -am test` must keep all S166 tests green. Runtime verification covers
PostgreSQL on 5443, app on 8093, health, OpenAPI, Swagger and `/fuel/`.
