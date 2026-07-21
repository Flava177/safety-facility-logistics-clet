package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * One reason a vehicle or driver is not ready: a machine-readable code, the human-readable
 * explanation, the severity and enough context to act on it.
 *
 * @param context identifiers and dates a console can link from — for example the expiring document's
 *        id and expiry date, or the conflicting trip's id
 */
public record ReadinessBlocker(
        ReadinessBlockerCode code,
        String message,
        BlockerSeverity severity,
        Map<String, Object> context) {

    public ReadinessBlocker {
        Objects.requireNonNull(code, "code is required");
        message = message == null || message.isBlank() ? code.message() : message.strip();
        severity = severity == null ? code.severity() : severity;
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public static ReadinessBlocker of(ReadinessBlockerCode code) {
        return new ReadinessBlocker(code, code.message(), code.severity(), Map.of());
    }

    public static ReadinessBlocker of(ReadinessBlockerCode code, Map<String, Object> context) {
        return new ReadinessBlocker(code, code.message(), code.severity(), context);
    }

    /** Adds detail to the standard message without losing the code's stable wording. */
    public static ReadinessBlocker of(ReadinessBlockerCode code, String detail, Map<String, Object> context) {
        String message = detail == null || detail.isBlank() ? code.message() : code.message() + " " + detail;
        return new ReadinessBlocker(code, message, code.severity(), context);
    }

    public boolean isBlocking() {
        return severity == BlockerSeverity.BLOCKING;
    }
}
