package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnsafeUploadException;
import java.util.Map;

/**
 * The size ceiling for a bulk data import, which is not the ceiling for a photograph.
 *
 * <h2>Why this is a second limit and not the same one</h2>
 *
 * <p>{@link UploadedFileScanner#MAX_BYTES} governs evidence: a phone photograph of a pump display, a
 * scanned certificate. Five megabytes clears the largest of those comfortably, and keeping it low
 * bounds the row, the heap and the cost of every content check the scanner runs.
 *
 * <p>A CSV import is a different thing wearing the same clothes. It is a month of a fuel provider's
 * transactions or a scanner's whole batch - bulk data, arriving once, from a system rather than a
 * person - and nothing about the reasoning behind the evidence cap applies to it. Briefly the two
 * shared a limit and a provider's monthly file would have been refused for being what it is.
 *
 * <p>Twenty megabytes is about a hundred and forty thousand rows at the width these files run to,
 * which is more than a site produces in a month with room to be wrong about that.
 *
 * <h2>This is the only bound there was ever going to be</h2>
 *
 * <p>Neither import path checked its input at all: the container's {@code max-file-size} was the
 * whole protection, and that is a blunt instrument which answers an oversized upload by failing the
 * request before any handler sees it - no error code, no field, nothing an operator can act on. So
 * raising that ceiling for imports without putting a real check underneath it would have removed the
 * only limit these endpoints had. The check belongs here, where the refusal can say what happened.
 */
public final class BulkImportPolicy {

    private BulkImportPolicy() {
    }

    public static final long MAX_BYTES = 20L * 1024 * 1024;

    /** The limit in whole megabytes, so no message restates the number and gets it wrong. */
    public static final long MAX_BYTES_MB = MAX_BYTES / (1024 * 1024);

    /**
     * Accepts the file or refuses it with a reason.
     *
     * @throws UnsafeUploadException empty, or past the ceiling
     */
    public static void requireWithinLimit(String fileName, byte[] content) {
        String safeName = fileName == null || fileName.isBlank() ? "the file" : fileName;
        if (content == null || content.length == 0) {
            throw new UnsafeUploadException(Map.of(
                    "reason", "The file is empty.",
                    "fileName", safeName));
        }
        if (content.length > MAX_BYTES) {
            throw new UnsafeUploadException(Map.of(
                    "reason", "The file is larger than the " + MAX_BYTES_MB + " MB import limit.",
                    "fileName", safeName,
                    "byteSize", content.length,
                    "maxBytes", MAX_BYTES));
        }
    }
}
