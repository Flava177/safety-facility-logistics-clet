# S168_fuel Operations and Verification Guide

## Local runtime

1. Start PostgreSQL: `docker compose -f compose.fleet-db.yml up -d fleet-postgres`.
2. Load Java 17 with `./use-sfl-env.ps1` or select Temurin 17 for the IntelliJ module.
3. Run `FleetLogisticsServiceApplication`. It uses `sfl__fleet_vehicle_service` on host port 5443 and HTTP port 8093.
4. Verify `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs`, and `/fuel/` on localhost:8093.

Development requests use `X-SFL-User`, `X-SFL-Display-Name`, `X-SFL-Roles`, `X-SFL-Sites`,
`X-SFL-Source-Channel`, `X-Correlation-ID` and `Idempotency-Key`. Use `FLEET_MANAGER` for full local review.

## First-use sequence

1. Register S166 vehicles/drivers and assign a trip where applicable.
2. Create an effective site policy with `POST /api/v1/fuel/policies`.
3. Capture a transaction, then call its `/reconcile` action.
4. Review anomalies through assign, review, explanation, decision and closure operations.
5. Create and submit a logbook, then review and approve it as a manager.

## CSV contract

`POST /api/v1/fuel/imports/csv` accepts UTF-8 CSV with headers:

`providerTransactionId,vehicleId,driverId,tripId,occurredAt,vendorReference,stationReference,fuelProduct,quantity,quantityUnit,unitPrice,totalCost,currency,cardReference,odometerReading,receiptEvidenceId,comments`

Blank optional UUIDs are accepted. `occurredAt` is ISO-8601. Quantity and money are decimal. Every row receives
a stable batch-row idempotency key and an accepted/rejected result.

## Verification and controls

From `services` with Java 17 run `mvn -pl sfl-fleet-logistics-service -am test`.

- Fuel/logbook readings never correct a regressed Fleet odometer.
- Approved logbooks are locked until privileged reopening.
- Anomalies cannot close without explanation, decision, evidence and reason.
- Provider duplicates are protected by provider identity and command idempotency.
- Vendor and Finance contracts remain ports until production specifications are approved.
