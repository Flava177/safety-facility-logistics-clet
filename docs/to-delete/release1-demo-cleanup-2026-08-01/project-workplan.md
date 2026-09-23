# SFL Spring Boot workplan

## Phase 0: Spring Boot foundation

Status: in progress, mostly complete.

- Maven Spring Boot project
- PostgreSQL datasource
- Flyway migration baseline
- IntelliJ run configuration
- Health/version endpoints
- First IFIMP facility-fault vertical slice
- Local development databases per deployable service

## Phase 1: Developer experience and architecture guardrails

Status: current focus.

- Local/dev/test/prod profile files
- IntelliJ one-click run flow
- Local run and smoke-test scripts
- Clean architecture documentation
- Migration rules
- Shared API conventions
- Correlation ID convention
- Consistent exception response model

## Phase 2: Identity and security

- Keycloak realm configuration
- JWT validation
- Role mapping
- Permission model
- Protected API tests

## Phase 3: IFIMP core

- Sites and locations
- Assets/equipment
- Facility fault workflow
- Work orders
- Technician assignments
- Preventive maintenance
- SLA and priority tracking

## Phase 4: Messaging and background processing

- Transactional outbox processor
- RabbitMQ publishing
- Notification hooks
- Scheduled maintenance jobs

## Phase 5: API contract and client readiness

- OpenAPI/Swagger
- Stable request/response DTOs
- Mobile/web API alignment
- Pagination and filtering conventions

## Phase 6: Testing and hardening

- Unit tests
- Integration tests with PostgreSQL
- Migration verification
- Security tests
- API smoke tests
- Logging and observability

## Phase 7: Deployment

- Dockerfile
- Docker Compose local stack
- Production profile
- Environment variable documentation
- CI build and test pipeline
