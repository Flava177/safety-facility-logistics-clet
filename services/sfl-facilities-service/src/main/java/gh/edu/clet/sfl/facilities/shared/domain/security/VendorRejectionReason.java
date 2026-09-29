package gh.edu.clet.sfl.facilities.shared.domain.security;

/**
 * Why a vendor message was refused. Recorded, audited and forwarded to the SIEM; never told to the sender.
 *
 * <p>The HTTP response to every one of these is the same {@code 401} and the same sentence. A sender
 * that could tell "wrong signature" from "unknown source" could enumerate which source ids exist.
 */
public enum VendorRejectionReason {
    SOURCE_NOT_ALLOWED,
    CHANNEL_NOT_PERMITTED,
    SITE_NOT_PERMITTED,
    SIGNATURE_MISSING,
    SIGNATURE_INVALID,
    TIMESTAMP_OUTSIDE_WINDOW,
    SCHEMA_INVALID
}
