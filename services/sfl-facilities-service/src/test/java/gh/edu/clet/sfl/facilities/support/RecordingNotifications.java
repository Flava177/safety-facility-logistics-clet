package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Captures what would have been sent, so a test can assert that somebody was told.
 *
 * <p>The distinction this double preserves is the one the requirement turns on. SRS-SFL-S153-02 says
 * the sweep "escalates the item <em>and notifies the configured role</em>", and for three passes only
 * the first half happened — the escalation moved a level and published an event nobody consumed, and
 * every test asserted on the level. So a test that checks escalation without checking notification
 * proves half a requirement and reads like it proves all of it.
 */
public final class RecordingNotifications implements NotificationPort {

    /** One notification that would have been sent. */
    public record Sent(String siteCode, String recipientType, String recipient, NotificationKind kind,
            String subjectReference, Map<String, String> context) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void notifyRecipient(String siteCode, String recipient, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        sent.add(new Sent(siteCode, "USER", recipient, kind, subjectReference, context));
    }

    @Override
    public void notifyRole(String siteCode, SflRole role, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        sent.add(new Sent(siteCode, "ROLE", role.name(), kind, subjectReference, context));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** Everything sent about one subject — a work-order or fault number. */
    public List<Sent> about(String subjectReference) {
        return sent.stream().filter(s -> s.subjectReference().equals(subjectReference)).toList();
    }

    public void clear() {
        sent.clear();
    }
}
