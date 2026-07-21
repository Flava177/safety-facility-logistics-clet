# ADR 0003: Migrate the SFL backend to Java and Spring Boot

## Status

Accepted for incremental migration.

## Decision

The SFL backend will move from C#/.NET to Java 17 and Spring Boot 4.1 while retaining the Phase 1
architecture: DDD-oriented modular monolith, package-enforced bounded contexts, PostgreSQL schemas
per context, transactional outbox, Keycloak, Redis, RabbitMQ, and external-system adapters.

Migration is incremental. The existing C# implementation is retained as executable specification
until each slice has API, behavior, persistence, authorization, and test parity in Java.

## Initial package map

| Existing project | Java package |
|---|---|
| `SFL.Api` | `gh.edu.clet.sfl.*.api` |
| `SFL.IFIMP` | `gh.edu.clet.sfl.ifimp` |
| `SFL.SSEMP` | `gh.edu.clet.sfl.ssemp` |
| `SFL.FTLMP` | `gh.edu.clet.sfl.ftlmp` |
| `SFL.IdentityAccess` | `gh.edu.clet.sfl.platform.security` |
| `SFL.AuditEvidence` | `gh.edu.clet.sfl.platform.audit` |
| `SFL.Messaging` | `gh.edu.clet.sfl.platform.messaging` |
| `SFL.IntegrationHub` | `gh.edu.clet.sfl.integration` |

## Consequences

- Existing EF Core migrations are not reused by Flyway.
- Java development starts on a separate `sfl_java` database.
- Data migration will be scripted and reconciled before cutover.
- The C# tree is removed only after the final parity and rollback gate is approved.

