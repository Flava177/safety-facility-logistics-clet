package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnsafeUploadException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

/**
 * What a file has to survive before the platform will keep it.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>Until now no SFL service accepted bytes. Evidence was metadata - a storage reference and a
 * SHA-256 the browser computed - and the file itself was somebody else's problem. Compliance
 * documents, fuel receipts and pump photographs all need the actual file, so the bytes arrive here,
 * and the moment a service accepts an upload it has acquired every upload vulnerability there is.
 *
 * <h2>Four checks, and each one is load-bearing</h2>
 *
 * <ol>
 *   <li><b>The extension</b> must be pdf, jpg or jpeg. Necessary and nowhere near sufficient: it is
 *       chosen by whoever uploads.</li>
 *   <li><b>The magic bytes</b> must say the same thing. This is the check that matters. A file named
 *       {@code certificate.pdf} that begins {@code MZ} is a Windows executable, and an extension
 *       allowlist alone waves it through.</li>
 *   <li><b>The declared content type</b> must agree with both. A browser that says {@code text/html}
 *       about something starting {@code %PDF-} is describing an attack, not a document.</li>
 *   <li><b>A PDF must carry no active content.</b> PDF is a scripting host: {@code /JavaScript} runs
 *       on open, {@code /Launch} starts a program, {@code /EmbeddedFile} smuggles a payload past
 *       every check above by carrying it inside a structurally valid PDF.</li>
 * </ol>
 *
 * <h2>What this is not</h2>
 *
 * <p>It is not an antivirus, and pretending otherwise would be the dangerous part. A PDF may hold its
 * objects in compressed streams, and a token inside one is invisible to a byte scan - so a determined
 * attacker with a crafted PDF gets past step 4. What it stops is the whole realistic range for this
 * platform: a renamed executable, a polyglot, an HTML file with a JPEG extension, a mail-merged PDF
 * dropper. Real scanning belongs in an ICAP or ClamAV sidecar; when one exists it goes <em>behind</em>
 * this, not instead of it, because every check here is free and none of them can be turned off by a
 * scanner being down. See the gap report.
 *
 * <p>The digest is computed <b>here, from the bytes received</b>, and never taken from the client. A
 * hash the uploader supplies attests to nothing: the point of the evidence chain is that the platform
 * can prove what it stored.
 */
public final class UploadedFileScanner {

    private UploadedFileScanner() {
    }

    /** The kinds of file the platform will hold. Nothing else is storable, by design. */
    public enum Kind {
        PDF("application/pdf", "pdf"),
        JPEG("image/jpeg", "jpg", "jpeg");

        private final String contentType;
        private final String[] extensions;

        Kind(String contentType, String... extensions) {
            this.contentType = contentType;
            this.extensions = extensions;
        }

        public String contentType() {
            return contentType;
        }

        boolean allows(String extension) {
            for (String candidate : extensions) {
                if (candidate.equals(extension)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Ten megabytes.
     *
     * <p>A phone photograph of a pump display is one to four; a scanned roadworthiness certificate is
     * under two. The cap is here because the bytes go into a database column, so an unbounded upload
     * is an unbounded row - and because it bounds the cost of every check below.
     */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** The wording the API and the dashboard both use for the accepted set. */
    public static final String ACCEPTED_DESCRIPTION = "PDF, JPG or JPEG";

    /**
     * PDF constructs that make a document do something rather than say something.
     *
     * <p>Every token here is at least seven characters, and that is deliberate rather than tidy. A
     * scan for a short token like {@code /JS} or {@code /AA} across ten megabytes of compressed image
     * data will match by coincidence roughly half the time, and a compliance certificate refused for
     * a random byte sequence teaches operators that the check is noise. Long tokens make a false
     * positive vanishingly unlikely, so a refusal here means something.
     */
    private static final String[] ACTIVE_CONTENT_TOKENS = {
        "/JavaScript", "/OpenAction", "/EmbeddedFile", "/RichMedia", "/Launch", "/XFA"
    };

    /** Everything the platform learned from the bytes, as opposed to from whoever sent them. */
    public record Verdict(Kind kind, String contentType, String fileName, String sha256Hash, long byteSize) {
    }

    /**
     * Accepts the file or refuses it with a reason.
     *
     * @param originalFileName    the client's name for it; treated as untrusted text
     * @param declaredContentType the client's claim about its type; may be null or blank
     * @param content             the bytes as received
     * @throws UnsafeUploadException if any check fails
     */
    public static Verdict scan(String originalFileName, String declaredContentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw refuse("The file is empty.", Map.of("fileName", safeName(originalFileName)));
        }
        if (content.length > MAX_BYTES) {
            throw refuse("The file is larger than the 10 MB limit.", Map.of(
                    "fileName", safeName(originalFileName),
                    "byteSize", content.length,
                    "maxBytes", MAX_BYTES));
        }

        String fileName = safeName(originalFileName);
        String extension = extensionOf(fileName);
        Kind sniffed = sniff(content);

        if (sniffed == null) {
            throw refuse("The file is not a " + ACCEPTED_DESCRIPTION + " file. Only " + ACCEPTED_DESCRIPTION
                    + " files can be uploaded.", Map.of("fileName", fileName, "declaredContentType",
                    declaredContentType == null ? "" : declaredContentType));
        }
        if (!sniffed.allows(extension)) {
            // The interesting case, and the reason the message names both: the contents and the name
            // disagree, which is what a renamed file looks like.
            throw refuse("The file contents are " + sniffed.contentType() + " but the name ends in \"."
                    + extension + "\". Rename it or upload the right file.", Map.of(
                    "fileName", fileName, "detectedType", sniffed.contentType(), "extension", extension));
        }
        if (!declaredTypeAgrees(declaredContentType, sniffed)) {
            throw refuse("The file was sent as \"" + declaredContentType + "\" but its contents are "
                    + sniffed.contentType() + ".", Map.of(
                    "fileName", fileName, "declaredContentType", declaredContentType,
                    "detectedType", sniffed.contentType()));
        }
        if (sniffed == Kind.PDF) {
            requireInertPdf(content, fileName);
        }

        return new Verdict(sniffed, sniffed.contentType(), fileName, sha256Hex(content), content.length);
    }

    /**
     * The file name, reduced to something safe to store, log and echo back.
     *
     * <p>Browsers send the base name, but a crafted client sends whatever it likes, and this name ends
     * up in a {@code Content-Disposition} header and a database column. Path separators are stripped
     * because {@code ../../etc/passwd} must not survive into any code that later joins it to a path;
     * control characters go because a carriage return in a header value is response splitting.
     */
    private static String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "upload";
        }
        String name = value.strip();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}\"\\\\]", "");
        // A leading dot hides the file on unix-like systems and produces an empty extension.
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            return "upload";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * The kind the bytes actually are, or null for anything else.
     *
     * <p>Both signatures are checked at offset zero. Tolerating leading junk is how polyglot files
     * work - a document that is a valid PDF <em>and</em> a valid something-else depending on which
     * end reads it - so the file must begin as what it claims to be.
     */
    private static Kind sniff(byte[] content) {
        if (startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'})) {
            return Kind.PDF;
        }
        // FF D8 FF opens every JPEG variant: JFIF, Exif and the raw APPn forms a phone camera writes.
        if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Kind.JPEG;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the client's claim about the type is compatible with what the bytes say.
     *
     * <p>An absent or {@code application/octet-stream} claim is accepted: it says "I do not know",
     * which is honest and common from a mobile browser. A confident and wrong claim is refused.
     */
    private static boolean declaredTypeAgrees(String declared, Kind sniffed) {
        if (declared == null || declared.isBlank()) {
            return true;
        }
        String type = declared.strip().toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters).strip();
        }
        if (type.equals("application/octet-stream")) {
            return true;
        }
        if (sniffed == Kind.JPEG) {
            return type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("image/pjpeg");
        }
        return type.equals("application/pdf") || type.equals("application/x-pdf");
    }

    /**
     * Refuses a PDF that can act.
     *
     * <p>Also refuses one with no {@code %%EOF} at all, which is not a security property but a
     * truncation check: a half-uploaded certificate is not evidence of anything, and finding that out
     * at upload time is much cheaper than finding out during an audit.
     */
    private static void requireInertPdf(byte[] content, String fileName) {
        // ISO-8859-1 maps every byte to exactly one character, so the search is over the real bytes
        // and no multi-byte decoding can merge or drop one. UTF-8 would mangle binary streams.
        String body = new String(content, StandardCharsets.ISO_8859_1);
        for (String token : ACTIVE_CONTENT_TOKENS) {
            if (body.contains(token)) {
                throw refuse("This PDF contains active content (" + token + ") and cannot be uploaded. "
                        + "Print or re-export it as a plain PDF and try again.", Map.of(
                        "fileName", fileName, "construct", token));
            }
        }
        if (!body.contains("%%EOF")) {
            throw refuse("The PDF is incomplete or damaged. Upload it again.", Map.of("fileName", fileName));
        }
    }

    /** SHA-256 of the bytes as stored, lower-case hex - the form the evidence chain compares. */
    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every Java SE implementation; its absence is not recoverable.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static UnsafeUploadException refuse(String reason, Map<String, Object> details) {
        Map<String, Object> full = new java.util.LinkedHashMap<>(details);
        full.put("reason", reason);
        return new UnsafeUploadException(full);
    }
}
