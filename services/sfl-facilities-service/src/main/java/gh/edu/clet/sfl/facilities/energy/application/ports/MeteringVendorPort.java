package gh.edu.clet.sfl.facilities.energy.application.ports;

import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * The vendor-specific half of AMI ingestion - SRS-SFL-S157-01 "via vendor API/AMI gateway behind the
 * integration boundary", SRS 2.6 "a domain module must never depend directly on a vendor API".
 *
 * <p>By the time this port sees a payload, {@code VendorMessageVerifier} has authenticated it. What is
 * left is vendor vocabulary: which field names the meter, how the interval is expressed, what the vendor
 * calls a gallon. The shipped adapter is {@code SimulatedMeteringAdapter}; a procured product gets its own
 * adapter and nothing in the application or domain changes.
 *
 * <p>Two calls rather than one because the conversion depends on the meter's utility - a litre of water
 * and a litre of diesel convert differently - and only S157 knows which meter a reference names.
 */
public interface MeteringVendorPort {

    /** The name reported by the health view and the procurement-gate register. */
    String adapterName();

    /**
     * The payload fields this vendor's message cannot be acted on without, handed to the verifier so a
     * message missing one is a schema rejection (NFR-SEC2) before anything else reads it.
     */
    java.util.List<String> requiredFields();

    /**
     * The vendor's meter reference in this payload.
     *
     * @throws UntranslatableMessageException when the payload names no meter
     */
    String meterReference(Map<String, Object> payload);

    /**
     * The payload's interval consumption, converted to the canonical unit of {@code utility}.
     *
     * @throws UntranslatableMessageException for an unknown unit, a unit of another utility, a
     *         non-numeric value or an unreadable interval - each recorded as a rejection, never guessed
     */
    MeterInterval translate(Map<String, Object> payload, Utility utility);

    /** One interval, canonical. {@code vendorValue}/{@code vendorUnit} are kept for audit of the conversion. */
    record MeterInterval(Instant intervalStart, Instant intervalEnd, BigDecimal consumption, BigDecimal vendorValue,
            String vendorUnit) {
    }

    final class UntranslatableMessageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UntranslatableMessageException(String detail) {
            super(detail);
        }
    }
}
