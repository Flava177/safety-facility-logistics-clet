package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The price a vendor posts for a product, over a period.
 *
 * <h2>Why the platform holds this at all</h2>
 *
 * <p>Because it is the one number in a fuel transaction that is not the driver's to choose. Litres
 * and cost are both observations of what happened; the price per litre is a fact about the
 * forecourt, the same for every vehicle that fills there that day. Leaving it as a free field made
 * a fuel claim a set of three numbers all supplied by the person being reimbursed, and any two of
 * them determine the third - so there was no arithmetic anyone could do to check the claim against
 * anything.
 *
 * <p>With the price held here, the driver enters what they paid, and the litres follow. That is the
 * whole mechanism: it converts an inflated <em>amount</em>, which nothing could detect, into an
 * inflated <em>volume</em>, which the tank capacity and consumption rules already detect.
 *
 * <h2>Effective dating, and what "in force" means</h2>
 *
 * <p>A transaction is judged against the price posted when it occurred, never the price posted now.
 * Fuel prices here move every few weeks; without this, every historical transaction would drift
 * into an anomaly the moment the pumps changed, and reconciliation would stop being reproducible -
 * which is the same reason {@link FuelPolicy} is effective-dated.
 */
public record FuelPostedPrice(
        UUID id,
        SiteCode siteCode,
        String vendor,
        String fuelProduct,
        BigDecimal unitPrice,
        String currency,
        Instant effectiveFrom,
        Instant effectiveTo,
        Source source,
        String notes,
        RecordMetadata metadata) {

    /**
     * Where the figure came from, which is the first thing asked when one is disputed.
     *
     * <p>Not cosmetic: a price a person typed and a price a provider's feed delivered are different
     * kinds of evidence, and once a month has passed nobody can tell them apart from the number
     * alone.
     */
    public enum Source {
        /**
         * Entered by a fleet administrator, typically from the forecourt sign or a price circular.
         */
        ADMINISTERED,
        /** Delivered by the provider's own integration. */
        PROVIDER_FEED,
        /** Derived from an accepted invoice during reconciliation. */
        INVOICE
    }

    public FuelPostedPrice {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(metadata, "metadata is required");
        // Upper-cased to match FuelPolicy's normalisation of its approved vendor set. A price filed
        // under "GOIL" and a policy allowing "Goil" would otherwise be two different vendors, and
        // the
        // price lookup would silently find nothing - which reads as "no reference price" rather
        // than
        // as the configuration mistake it is.
        vendor = require(vendor, "vendor").toUpperCase(Locale.ROOT);
        fuelProduct = require(fuelProduct, "fuelProduct").toUpperCase(Locale.ROOT);
        currency = require(currency, "currency").toUpperCase(Locale.ROOT);
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be greater than zero");
        }
        unitPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must follow effectiveFrom");
        }
        notes = notes == null || notes.isBlank() ? null : notes.strip();
    }

    public boolean appliesAt(Instant at) {
        return !at.isBefore(effectiveFrom) && (effectiveTo == null || at.isBefore(effectiveTo));
    }

    /**
     * Closes an open price so a new one can take effect, without losing what was in force before.
     */
    public FuelPostedPrice supersededAt(Instant at, RecordMetadata changed) {
        if (effectiveTo != null) {
            throw new IllegalStateException("this price is already closed");
        }
        return new FuelPostedPrice(
                id,
                siteCode,
                vendor,
                fuelProduct,
                unitPrice,
                currency,
                effectiveFrom,
                at,
                source,
                notes,
                changed);
    }

    /**
     * How far {@code observed} is from this price, as a fraction of it.
     *
     * <p>Absolute, because paying conspicuously <em>less</em> than the posted price is as much a
     * question as paying more - it is what a receipt for fuel that never went into the tank looks
     * like.
     */
    public BigDecimal deviationFrom(BigDecimal observed) {
        return observed.subtract(unitPrice).abs().divide(unitPrice, 4, RoundingMode.HALF_UP);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
