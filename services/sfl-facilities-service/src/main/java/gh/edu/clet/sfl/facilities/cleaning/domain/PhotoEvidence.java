package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A reference to a photograph and the SHA-256 of its bytes - SRS-SFL-S169-02 "photo evidence".
 *
 * <p>Never the photograph. This build has no object store, so the mobile checklist client keeps the
 * image wherever CLET's evidence storage turns out to be and hands S169 a reference plus the hash of
 * what it stored. The hash is what makes the reference worth holding: a photo swapped after the fact
 * no longer matches, and "cleaning quality is verifiable, not just claimed" depends on exactly that.
 *
 * <p>An inline {@code data:} URI is refused. It would be the binary smuggled in through the reference
 * field, which is the thing this value exists to keep out of the database.
 */
public record PhotoEvidence(String reference, String contentHash) {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final int MAX_REFERENCE = 500;

    public PhotoEvidence {
        if (reference == null || reference.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A photo evidence reference is required.");
        }
        reference = reference.strip();
        if (reference.length() > MAX_REFERENCE) {
            throw new FacilitiesException.ValidationFailedException(
                    "A photo evidence reference may be at most " + MAX_REFERENCE + " characters. "
                            + "Send a reference to the stored image, not the image.");
        }
        if (reference.toLowerCase(Locale.ROOT).startsWith("data:")) {
            throw new FacilitiesException.ValidationFailedException(
                    "Photo evidence must be a reference to a stored image; inline image data is not accepted.");
        }
        contentHash = contentHash == null ? null : contentHash.strip().toLowerCase(Locale.ROOT);
        if (contentHash == null || !SHA_256_HEX.matcher(contentHash).matches()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Photo evidence needs the SHA-256 content hash of the stored image (64 hexadecimal characters).");
        }
    }

    /** Both halves or neither: a reference with no hash cannot be verified. */
    public static PhotoEvidence ofNullable(String reference, String contentHash) {
        boolean noReference = reference == null || reference.isBlank();
        boolean noHash = contentHash == null || contentHash.isBlank();
        if (noReference && noHash) {
            return null;
        }
        return new PhotoEvidence(reference, contentHash);
    }
}
