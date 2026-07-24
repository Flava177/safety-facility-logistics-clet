package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral fuel transaction with immutable source provenance. */
public record FuelTransaction(UUID id, SiteCode siteCode, String providerTransactionId, String sourceSystem,
        UUID vehicleId, UUID driverId, UUID tripId, Instant occurredAt, String vendorReference,
        String stationReference, String fuelProduct, BigDecimal quantity, String quantityUnit,
        BigDecimal unitPrice, BigDecimal totalCost, Currency currency, String maskedCardReference,
        long odometerReading, UUID receiptEvidenceId, String comments, Status status, Lifecycle lifecycle,
        Instant ingestionTimestamp, String idempotencyKey, RecordMetadata metadata) {

    public enum Status { RECEIVED, VALIDATING, MATCHED, RECONCILED, EXCEPTION, REJECTED, VOIDED }
    public enum Lifecycle { ACTIVE, VOIDED, ARCHIVED }

    public FuelTransaction {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(driverId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(currency);
        Objects.requireNonNull(ingestionTimestamp); Objects.requireNonNull(status); Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(metadata);
        sourceSystem = require(sourceSystem, "sourceSystem"); vendorReference = require(vendorReference, "vendorReference");
        fuelProduct = require(fuelProduct, "fuelProduct").toUpperCase(); quantityUnit = require(quantityUnit, "quantityUnit").toUpperCase();
        if (quantity == null || quantity.signum() <= 0 || unitPrice == null || unitPrice.signum() < 0 || odometerReading < 0) {
            throw new IllegalArgumentException("fuel quantity, price or odometer is invalid");
        }
        quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        unitPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
        BigDecimal calculated = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        totalCost = totalCost == null ? calculated : totalCost.setScale(2, RoundingMode.HALF_UP);
        if (totalCost.compareTo(calculated) != 0) throw new IllegalArgumentException("totalCost must equal quantity multiplied by unitPrice");
        providerTransactionId = trim(providerTransactionId); stationReference = trim(stationReference);
        maskedCardReference = mask(maskedCardReference); comments = trim(comments); idempotencyKey = trim(idempotencyKey);
    }

    public FuelTransaction withStatus(Status next, RecordMetadata changed) {
        if (status == Status.VOIDED || lifecycle != Lifecycle.ACTIVE) throw new IllegalStateException("fuel transaction is immutable");
        return new FuelTransaction(id, siteCode, providerTransactionId, sourceSystem, vehicleId, driverId, tripId,
                occurredAt, vendorReference, stationReference, fuelProduct, quantity, quantityUnit, unitPrice,
                totalCost, currency, maskedCardReference, odometerReading, receiptEvidenceId, comments, next,
                lifecycle, ingestionTimestamp, idempotencyKey, changed);
    }

    public FuelTransaction voided(String reason, RecordMetadata changed) {
        require(reason, "void reason");
        return new FuelTransaction(id, siteCode, providerTransactionId, sourceSystem, vehicleId, driverId, tripId,
                occurredAt, vendorReference, stationReference, fuelProduct, quantity, quantityUnit, unitPrice,
                totalCost, currency, maskedCardReference, odometerReading, receiptEvidenceId, reason, Status.VOIDED,
                Lifecycle.VOIDED, ingestionTimestamp, idempotencyKey, changed);
    }

    private static String mask(String value) {
        value = trim(value); if (value == null || value.startsWith("****")) return value;
        String last = value.length() <= 4 ? value : value.substring(value.length() - 4);
        return "****" + last;
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.strip(); }
}
