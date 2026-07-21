# sfl-fleet-logistics-service

Fleet and logistics service for S166, S168_fuel and S171.

Boundary rules:
- Own this service's database schema only: $(System.Collections.Hashtable.Schema).
- Publish cross-service changes through the service outbox.
- Consume external events idempotently through the service inbox.
- Store vendor payloads through adapters; do not leak vendor models into domain packages.
- Store evidence file references and hashes only; do not store large files or CCTV video in this database.

