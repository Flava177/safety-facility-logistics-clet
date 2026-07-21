# ADR 0001: Phase 1 Foundation Stack

## Status

Accepted

## Decision

SFL Phase 1 uses DDD, Clean Architecture, SOLID, PostgreSQL, Redis, RabbitMQ, Keycloak, Docker, and explicit messaging/cache/identity/integration ports.

RabbitMQ is the default Phase 1 messaging adapter through `SFL.Messaging`. Kafka is not used in Phase 1 unless a future streaming requirement justifies adding a separate adapter.

The Phase 1 delivery style is a modular monolith with clean internal boundaries, event-first module coordination and adapter-based integration with external systems.

## Consequences

Application and domain code must not depend directly on RabbitMQ, Redis, Keycloak, PostgreSQL, or vendor SDKs. Infrastructure adapters provide concrete implementations behind ports.

All business events that leave a module should pass through the messaging abstraction and should use inbox/outbox patterns for reliability and idempotency.

External hardware or vendor systems must be integrated through `SFL.IntegrationHub` and `SFL.Infrastructure.ExternalSystems`, not directly from domain modules.
