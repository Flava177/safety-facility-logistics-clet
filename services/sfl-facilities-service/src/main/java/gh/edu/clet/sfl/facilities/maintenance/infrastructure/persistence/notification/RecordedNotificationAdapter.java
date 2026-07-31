package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.notification;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 IFIMP notification adapter: records the intent, never claims the delivery.
 *
 * <p>No notification provider is procured for IFIMP, and the honest response to that is a durable row
 * an operator can read and reconcile — not a log line, and above all not a silent success. "The
 * technician was notified" and "we wrote that the technician should be notified" are different claims,
 * and only one of them is true today. This adapter makes the true one auditable.
 *
 * <p><strong>It fails loudly when misconfigured.</strong> If {@code sfl.facilities.notification.provider}
 * names a real provider, resolution throws at the first send rather than quietly degrading to the
 * recorded behaviour. A half-configured environment that looks like it is notifying people is worse
 * than one that plainly is not — the same rule fleet applies for C-11.
 *
 * <p><strong>It runs in its own transaction.</strong> {@code REQUIRES_NEW}, because a notification is
 * evidence that an escalation happened and must survive the caller rolling back. The inverse — losing
 * the escalation but keeping the page — is the less damaging of the two failures, and the sweep is
 * idempotent, so a replayed escalation will not double-notify.
 */
@Component
public class RecordedNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedNotificationAdapter.class);
    private static final String RECORDED_PROVIDER = "recorded";

    private final NotificationIntentRepository intents;
    private final Clock clock;
    private final String configuredProvider;

    RecordedNotificationAdapter(NotificationIntentRepository intents, Clock clock,
            @Value("${sfl.facilities.notification.provider:recorded}") String configuredProvider) {
        this.intents = intents;
        this.clock = clock;
        this.configuredProvider = configuredProvider;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyRecipient(String siteCode, String recipient, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        requireSupportedProvider();
        if (recipient == null || recipient.isBlank()) {
            // Unassigned work has nobody to tell, which is a fact about the work rather than a failure.
            // Logged rather than thrown: refusing here would make an escalation depend on an assignment.
            log.warn("IFIMP notification {} for {} has no recipient; nothing recorded", kind, subjectReference);
            return;
        }
        record(siteCode, NotificationIntentEntity.RECIPIENT_USER, recipient, kind, subjectReference, context);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyRole(String siteCode, SflRole role, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        requireSupportedProvider();
        record(siteCode, NotificationIntentEntity.RECIPIENT_ROLE, role.name(), kind, subjectReference, context);
    }

    private void record(String siteCode, String recipientType, String recipient, NotificationKind kind,
            String subjectReference, Map<String, String> context) {
        intents.save(new NotificationIntentEntity(UUID.randomUUID(), siteCode, recipientType, recipient, kind,
                subjectReference, canonicalJson(context), clock.instant()));
        log.info("IFIMP notification recorded: kind={} recipientType={} recipient={} subject={} site={}",
                kind, recipientType, recipient, subjectReference, siteCode);
    }

    private void requireSupportedProvider() {
        if (!RECORDED_PROVIDER.equalsIgnoreCase(configuredProvider.strip())) {
            throw new IllegalStateException("sfl.facilities.notification.provider is '" + configuredProvider
                    + "' but only '" + RECORDED_PROVIDER + "' is implemented. Configure a real provider adapter "
                    + "or set it back to 'recorded' — this will not silently fall back.");
        }
    }

    /**
     * Sorted keys and minimal escaping, so two rows recording the same facts compare equal.
     *
     * <p>Written by hand rather than through the object mapper for the reason the audit chain learned
     * the hard way: a general-purpose mapper's property order is not stable between versions, and this
     * column is stored as {@code jsonb}, which reorders keys again on the way in. Sorting here means
     * the value is at least deterministic before PostgreSQL touches it.
     */
    private static String canonicalJson(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder("{");
        new TreeMap<>(context).forEach((key, value) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append('"').append(escape(key)).append("\":\"").append(escape(String.valueOf(value))).append('"');
        });
        return out.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
