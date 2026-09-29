package gh.edu.clet.sfl.facilities.construction.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The S176 integration events, named once (SRS CORR-04). Every one is documented, with its payload, in
 * {@code docs/facilities/S176_Event_Contracts.md}.
 *
 * <p>Payloads carry references and classifications only - ids, codes, amounts, dates - never free
 * text a person typed (a variation's justification, a defect's description), so nothing a contractor
 * or a member of staff wrote leaves this service on the broker.
 */
public final class ConstructionEvents {

    public static final String PROJECT_REGISTERED = "sfl.ifimp.project-registered.v1";
    public static final String PROJECT_APPROVED = "sfl.ifimp.project-approved.v1";
    public static final String PROJECT_STARTED = "sfl.ifimp.project-started.v1";
    public static final String VARIATION_APPROVED = "sfl.ifimp.project-variation-approved.v1";
    public static final String VARIATION_ESCALATION_REQUIRED = "sfl.ifimp.project-variation-escalation-required.v1";
    public static final String SITE_ACCESS_REQUESTED = "sfl.ifimp.contractor-site-access-requested.v1";
    public static final String SITE_ACCESS_SUSPENDED = "sfl.ifimp.contractor-site-access-suspended.v1";
    public static final String PROJECT_HANDED_OVER = "sfl.ifimp.project-handed-over.v1";
    public static final String PROJECT_DEFECT_RAISED = "sfl.ifimp.project-defect-raised.v1";
    public static final String PROJECT_CLOSED = "sfl.ifimp.project-closed.v1";

    /** Consumed: reserved for S164 (SSEMP), which is not built. Nothing publishes these yet. */
    public static final String PERMIT_ISSUED = "sfl.ssemp.permit-issued.v1";
    public static final String PERMIT_SUSPENDED = "sfl.ssemp.permit-suspended.v1";
    public static final String PERMIT_EXTENDED = "sfl.ssemp.permit-extended.v1";
    public static final String PERMIT_CLOSED = "sfl.ssemp.permit-closed.v1";

    private ConstructionEvents() {
    }

    /** Builds a payload from alternating keys and values, leaving out nulls rather than publishing them. */
    public static Map<String, Object> payload(Object... keysAndValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                payload.put(String.valueOf(keysAndValues[i]), value instanceof Enum<?> e ? e.name()
                        : value instanceof java.util.UUID || value instanceof java.time.temporal.Temporal
                                ? value.toString() : value);
            }
        }
        return payload;
    }
}
