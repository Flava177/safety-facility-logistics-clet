# Database migrations

SFL Spring Boot uses Flyway for database schema changes.

Migration files live in:

```text
src/main/resources/db/migration
```

The current first migration is:

```text
V1__sfl_foundation.sql
```

## Rules

1. Every database schema change must be a new Flyway migration.
2. Do not edit a migration after it has been applied to a shared database.
3. Hibernate must validate the schema, not create or mutate it automatically.
4. PostgreSQL remains the source of truth for persisted schema state.

## Naming

Use Flyway's versioned naming convention:

```text
V2__add_work_orders.sql
V3__add_assets.sql
V4__add_maintenance_assignments.sql
```

The number must increase. The text after the double underscore should describe the change.

## What happens on app startup

When the app starts, Flyway:

1. Connects to PostgreSQL.
2. Checks `flyway_schema_history`.
3. Finds migrations that have not run yet.
4. Applies them inside PostgreSQL.
5. Records the successful version.

That gives us durable schema history without manually tracking schema versions.
