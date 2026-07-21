# sfl-safety-security-service

Safety and security service for S160, S160a, S161, S162, S162a, S163 and S174.

Boundary rules:
- Own this service's database schema only: $(System.Collections.Hashtable.Schema).
- Publish cross-service changes through the service outbox.
- Consume external events idempotently through the service inbox.
- Store vendor payloads through adapters; do not leak vendor models into domain packages.
- Store evidence file references and hashes only; do not store large files or CCTV video in this database.

