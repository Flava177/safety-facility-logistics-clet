package gh.edu.clet.sfl.common.web;

import java.util.regex.Pattern;

/**
 * Builds a safe {@code Content-Disposition} filename from a request-derived segment (a site code, a
 * report name) without trusting its characters.
 *
 * <p>A modern servlet container already rejects a raw CR/LF in a header value, so this is not closing
 * a response-splitting hole the container leaves open - it is closing the narrower one the container
 * does not: an unvalidated {@code "} or {@code ;} in the segment still corrupts the
 * {@code filename=} attribute for the browser parsing it, which is reason enough that a value this
 * plainly identifier-shaped (a site code) should never reach a header unvalidated in the first place.
 */
public final class ContentDispositionFilenames {

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_-]");

    private ContentDispositionFilenames() {
    }

    /**
     * Strips everything but letters, digits, {@code -} and {@code _} from {@code segment}, so the
     * result is always safe to place inside a {@code filename=...} attribute unquoted. A segment that
     * turns out to name no real record still reaches the application layer's own lookup and gets that
     * layer's own "not found"/"not authorised" answer - this only protects the header, not that check.
     */
    public static String sanitizeSegment(String segment) {
        return UNSAFE.matcher(segment == null ? "" : segment).replaceAll("");
    }
}
