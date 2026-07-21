# Clean architecture for SFL

The Spring Boot migration should stay as a modular monolith with clean architecture boundaries.

## Package shape

Each business capability should follow this structure:

```text
gh.edu.clet.sfl.<module>.<feature>.api
gh.edu.clet.sfl.<module>.<feature>.application
gh.edu.clet.sfl.<module>.<feature>.domain
gh.edu.clet.sfl.<module>.<feature>.infrastructure
```

Example:

```text
gh.edu.clet.sfl.ifimp.maintenance.api
gh.edu.clet.sfl.ifimp.maintenance.application
gh.edu.clet.sfl.ifimp.maintenance.domain
gh.edu.clet.sfl.ifimp.maintenance.infrastructure
```

## Dependency direction

The intended dependency direction is:

```text
api -> application -> domain
infrastructure -> application/domain
```

The domain layer should not depend on Spring, JPA, HTTP, RabbitMQ, Redis, or database concerns.

## Layer responsibilities

### API

- HTTP controllers
- Request/response DTOs
- Authentication principal mapping
- HTTP status codes

### Application

- Use cases
- Transaction boundaries
- Coordination between repositories, domain objects, audit, and outbox
- Application commands and results

### Domain

- Business entities
- Value objects
- Domain rules
- Status transitions

### Infrastructure

- JPA records/entities
- Spring Data repositories
- Message adapters
- External system clients
- Persistence mapping

## Migration discipline

The C# implementation is reference material until a Java slice reaches parity. For each migrated slice, confirm:

- API contract
- authorization behavior
- database schema
- business workflow
- tests
- operational logging/auditing
