package gh.edu.clet.sfl.facilities.shared.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * The tamper-evident audit hash chain required by SRS-SFL-S152-03 — "Audit records shall be
 * append-only and tamper-evident using a hash-chain or equivalent control".
 *
 * <p>{@code hash = SHA-256(previousHash ‖ canonical(record))}. The canonical form is a fixed field
 * order separated by the ASCII unit separator, a character that cannot occur in any audit field
 * value, so two different records can never canonicalise to the same string. An absent field and an
 * empty one are distinguished by a null marker, so {@code (null, "")} and {@code ("", null)} hash
 * differently.
 *
 * <p>Replaying the chain detects a mutated record (its hash no longer matches its content), a removed
 * record (the sequence gaps) and a reordered record (the previous-hash link breaks).
 *
 * <p>Deliberately identical in construction to the fleet service's chain
 * ({@code gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditHashChain}) so a compliance reviewer
 * verifying two SFL services is verifying one algorithm. It is duplicated rather than shared because
 * the canonical form includes this system's own field set; promoting it to
 * {@code sfl-service-common} would freeze that field set for every service at once.
 */
public final class AuditHashChain {

    /** Chain seed used as the previous hash of the very first record. */
    public static final String GENESIS_HASH = "0".repeat(64);

    /**
     * ASCII unit separator (0x1F): cannot occur in any audit field value, so canonical forms are
     * unambiguous. Written as a numeric constant rather than a unicode escape because Java processes
     * unicode escapes before parsing, which makes a control character in a literal both invisible in
     * review and fragile across encodings.
     */
    private static final char UNIT_SEPARATOR = 0x1F;
    /** ASCII record separator (0x1E): distinguishes an absent field from an empty one. */
    private static final String NULL_MARKER = String.valueOf((char) 0x1E);

    private AuditHashChain() {
    }

    /** Seals {@code event} at {@code sequenceNo}, linking it to {@code previousHash}. */
    public static AuditEvent seal(AuditEvent event, long sequenceNo, String previousHash) {
        Objects.requireNonNull(event, "event is required");
        String previous = normalise(previousHash);
        AuditEvent positioned = event.sealed(sequenceNo, previous, null);
        return positioned.sealed(sequenceNo, previous, computeHash(previous, positioned));
    }

    /** Recomputes the hash a record must carry given its predecessor's hash. */
    public static String computeHash(String previousHash, AuditEvent event) {
        Objects.requireNonNull(event, "event is required");
        return sha256Hex(normalise(previousHash) + UNIT_SEPARATOR + canonical(event));
    }

    /**
     * Replays an ordered chain segment.
     *
     * @param orderedEvents records ordered by ascending sequence number
     * @param expectedFirstPreviousHash the hash preceding the first record, or {@code null} when the
     *        segment starts at the beginning of the chain
     */
    public static AuditChainVerification verify(List<AuditEvent> orderedEvents, String expectedFirstPreviousHash) {
        Objects.requireNonNull(orderedEvents, "orderedEvents is required");
        String previousHash = normalise(expectedFirstPreviousHash);
        long expectedSequence = orderedEvents.isEmpty() ? 0L : orderedEvents.get(0).sequenceNo();
        long verified = 0L;

        for (AuditEvent event : orderedEvents) {
            if (event.sequenceNo() != expectedSequence) {
                return AuditChainVerification.brokenSequence(expectedSequence, event.sequenceNo(),
                        "Audit sequence is not contiguous", verified);
            }
            if (!previousHash.equals(event.previousHash())) {
                return AuditChainVerification.broken(event.sequenceNo(), previousHash, event.previousHash(),
                        "Previous-hash link does not match the preceding record", verified);
            }
            String recomputed = computeHash(previousHash, event);
            if (!recomputed.equals(event.recordHash())) {
                return AuditChainVerification.broken(event.sequenceNo(), recomputed, event.recordHash(),
                        "Record hash does not match the stored content", verified);
            }
            previousHash = event.recordHash();
            expectedSequence++;
            verified++;
        }
        return AuditChainVerification.intact(verified, previousHash);
    }

    private static String canonical(AuditEvent event) {
        StringBuilder builder = new StringBuilder(256);
        append(builder, Long.toString(event.sequenceNo()));
        append(builder, event.id().toString());
        append(builder, event.siteScope());
        append(builder, event.actorId());
        append(builder, event.actorDisplayName());
        append(builder, event.action().name());
        append(builder, event.resourceType());
        append(builder, event.resourceId());
        append(builder, event.beforeValue());
        append(builder, event.afterValue());
        append(builder, event.correlationId());
        append(builder, event.sourceChannel().name());
        append(builder, event.occurredAt().toString());
        return builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value == null ? NULL_MARKER : value).append(UNIT_SEPARATOR);
    }

    private static String normalise(String previousHash) {
        return previousHash == null || previousHash.isBlank() ? GENESIS_HASH : previousHash;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandated by every JRE; if it is missing the platform is unusable.
            throw new IllegalStateException("SHA-256 is not available in this runtime", exception);
        }
    }
}
