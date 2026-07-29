/**
 * Enumerations mirrored from `gh.edu.clet.sfl.fleetlogistics.fuel.domain.model`.
 *
 * Values are the wire values — the Java enum constant names, read off the records themselves and
 * confirmed against `/v3/api-docs`. Labels are UI-only; the service never sees them.
 *
 * `humanise` is not redefined here. It is one function and it belongs to whichever module declared
 * it first, so this module imports it from `modules/fleet/api/enums` exactly as `StatusChip` and
 * `fields.tsx` already do.
 */

export const FUEL_TRANSACTION_STATUSES = [
  'RECEIVED',
  'VALIDATING',
  'MATCHED',
  'RECONCILED',
  'EXCEPTION',
  'REJECTED',
  'VOIDED',
] as const;
export type FuelTransactionStatus = (typeof FUEL_TRANSACTION_STATUSES)[number];

/**
 * Statuses the enum declares but no code path writes.
 *
 * `capture` writes `RECEIVED`, `reconcile` writes `RECONCILED` or `EXCEPTION`, `voidTransaction`
 * writes `VOIDED`. Nothing produces the other three. They stay in the filter — a stored record could
 * carry any of them — but the lifecycle panel marks them so an operator does not wait for a
 * transition that will never arrive. Recorded as gap 8.
 */
export const UNREACHABLE_TRANSACTION_STATUSES: FuelTransactionStatus[] = [
  'VALIDATING',
  'MATCHED',
  'REJECTED',
];

export const FUEL_TRANSACTION_LIFECYCLES = ['ACTIVE', 'VOIDED', 'ARCHIVED'] as const;
export type FuelTransactionLifecycle = (typeof FUEL_TRANSACTION_LIFECYCLES)[number];

export const FUEL_POLICY_STATUSES = ['ACTIVE', 'INACTIVE', 'ARCHIVED'] as const;
export type FuelPolicyStatus = (typeof FUEL_POLICY_STATUSES)[number];

export const LOGBOOK_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'UNDER_REVIEW',
  'RETURNED',
  'RESUBMITTED',
  'APPROVED',
  'REOPENED',
  'CANCELLED',
] as const;
export type LogbookStatus = (typeof LOGBOOK_STATUSES)[number];

export const LOGBOOK_USE_CLASSIFICATIONS = ['OFFICIAL', 'PRIVATE', 'OPERATIONAL'] as const;
export type LogbookUseClassification = (typeof LOGBOOK_USE_CLASSIFICATIONS)[number];

export const ANOMALY_TYPES = [
  'DUPLICATE',
  'MISSING_RECEIPT',
  'LIMIT_EXCEEDED',
  'TANK_CAPACITY',
  'FUEL_PRODUCT',
  'IDENTITY_MISMATCH',
  'OUTSIDE_TRIP',
  'VEHICLE_UNAVAILABLE',
  'DRIVER_INELIGIBLE',
  'ODOMETER_REGRESSION',
  'ODOMETER_JUMP',
  'ABNORMAL_CONSUMPTION',
  'LOGBOOK_MISMATCH',
  'VENDOR',
  'UNUSUAL_PATTERN',
  'MISSING_LOGBOOK',
  'COST_VARIANCE',
] as const;
export type AnomalyType = (typeof ANOMALY_TYPES)[number];

export const ANOMALY_SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type AnomalySeverity = (typeof ANOMALY_SEVERITIES)[number];

export const ANOMALY_STATUSES = [
  'DETECTED',
  'ASSIGNED',
  'UNDER_REVIEW',
  'AWAITING_EXPLANATION',
  'EXPLANATION_RECEIVED',
  'APPROVED',
  'REJECTED',
  'ESCALATED',
  'CLOSED',
  'HELD',
  'CANCELLED',
  'REOPENED',
] as const;
export type AnomalyStatus = (typeof ANOMALY_STATUSES)[number];

export const ANOMALY_DECISIONS = ['APPROVED', 'REJECTED'] as const;
export type AnomalyDecision = (typeof ANOMALY_DECISIONS)[number];

/**
 * The rules `FuelApplicationService.reconcile` evaluates, in evaluation order.
 *
 * The per-rule outcomes are persisted to `fuel_reconciliations.rule_results` but no endpoint reads
 * them back (gap 1), so this list exists to name a rule when a failure surfaces through
 * `FuelAnomalyCase.detectedRules` — never to claim a rule passed.
 */
export const RECONCILIATION_RULES = [
  'MAX_PER_TRANSACTION',
  'TANK_CAPACITY',
  'FUEL_PRODUCT',
  'APPROVED_VENDOR',
  'DRIVER_ELIGIBLE',
  'VEHICLE_OPERATIONAL',
  'TRIP_MATCH',
  'ODOMETER_NON_REGRESSION',
  'ODOMETER_JUMP',
  'RECEIPT',
  'CONSUMPTION_RANGE',
  'COST_VARIANCE',
  'LOGBOOK_MATCH',
  'REPEATED_PATTERN',
] as const;
export type ReconciliationRule = (typeof RECONCILIATION_RULES)[number];

/** Which anomaly type each reconciliation rule raises, from the `check(...)` calls in the service. */
export const RULE_ANOMALY_TYPE: Record<ReconciliationRule, AnomalyType> = {
  MAX_PER_TRANSACTION: 'LIMIT_EXCEEDED',
  TANK_CAPACITY: 'TANK_CAPACITY',
  FUEL_PRODUCT: 'FUEL_PRODUCT',
  APPROVED_VENDOR: 'VENDOR',
  DRIVER_ELIGIBLE: 'DRIVER_INELIGIBLE',
  VEHICLE_OPERATIONAL: 'VEHICLE_UNAVAILABLE',
  TRIP_MATCH: 'OUTSIDE_TRIP',
  ODOMETER_NON_REGRESSION: 'ODOMETER_REGRESSION',
  ODOMETER_JUMP: 'ODOMETER_JUMP',
  RECEIPT: 'MISSING_RECEIPT',
  CONSUMPTION_RANGE: 'ABNORMAL_CONSUMPTION',
  COST_VARIANCE: 'COST_VARIANCE',
  LOGBOOK_MATCH: 'LOGBOOK_MISMATCH',
  REPEATED_PATTERN: 'UNUSUAL_PATTERN',
};

/** What each rule checks, in the operator's words. Shown beside a failed rule. */
export const RULE_DESCRIPTIONS: Record<ReconciliationRule, string> = {
  MAX_PER_TRANSACTION: 'Quantity is within the policy limit for a single transaction.',
  TANK_CAPACITY: 'Quantity does not exceed the tank capacity the policy records.',
  FUEL_PRODUCT: 'The product dispensed is one the policy allows.',
  APPROVED_VENDOR: 'The vendor is on the policy’s approved list.',
  DRIVER_ELIGIBLE: 'The driver was eligible at the time of the transaction.',
  VEHICLE_OPERATIONAL: 'The vehicle was active and not marked unavailable.',
  TRIP_MATCH: 'The transaction falls inside the trip it was booked against.',
  ODOMETER_NON_REGRESSION: 'The reading is not lower than the accepted vehicle odometer.',
  ODOMETER_JUMP: 'The reading advances by no more than the policy’s jump tolerance.',
  RECEIPT: 'A receipt is present, or the policy’s grace period has not yet elapsed.',
  CONSUMPTION_RANGE: 'Consumption since the previous transaction is inside the policy range.',
  COST_VARIANCE: 'Unit price is within 30% of the previous transaction for this vehicle.',
  LOGBOOK_MATCH: 'The reading agrees with the trip’s driver logbook.',
  REPEATED_PATTERN: 'Fewer than three anomalies for this vehicle or driver in the last 30 days.',
};

/** Rule names the service records for anomalies it raises outside reconciliation. */
export const NON_RECONCILIATION_RULES: Record<string, string> = {
  COMPLETED_TRIP_WITHOUT_LOGBOOK:
    'A trip was completed with no driver logbook against it. Raised by the overnight sweep.',
};

/** CSV import column headers, from `FuelImportService.toCommand`. Order is not significant. */
export const CSV_REQUIRED_HEADERS = [
  'vehicleId',
  'driverId',
  'occurredAt',
  'vendorReference',
  'fuelProduct',
  'quantity',
  'quantityUnit',
  'unitPrice',
  'currency',
  'odometerReading',
] as const;

export const CSV_OPTIONAL_HEADERS = [
  'providerTransactionId',
  'tripId',
  'stationReference',
  'totalCost',
  'cardReference',
  'receiptEvidenceId',
  'comments',
] as const;

/** Fuel products and units the dashboard offers. Free text on the wire — the policy is the authority. */
export const FUEL_PRODUCTS = ['DIESEL', 'PETROL', 'LPG', 'KEROSENE'] as const;
export const QUANTITY_UNITS = ['LITRE', 'GALLON', 'KILOGRAM'] as const;
export const CURRENCIES = ['GHS', 'USD', 'EUR', 'GBP'] as const;
