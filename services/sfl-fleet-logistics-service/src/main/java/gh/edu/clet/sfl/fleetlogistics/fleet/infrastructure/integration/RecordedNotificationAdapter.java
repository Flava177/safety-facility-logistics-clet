package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.integration;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IntegrationConfigurationNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit.CanonicalJson;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 notification adapter.
 *
 * <p>No fleet notification provider is procured for Phase 1 (gap report C-11), so this adapter records
 * a durable notification intent and logs it. That is an honest outcome an operator can inspect and
 * reconcile — it never reports a delivery that did not happen.
 *
 * <p>If {@code sfl.fleet.notification.provider} names a real provider, resolution fails loudly at the
 * first send rather than degrading to the recorded behaviour, so a mis-configured environment is
 * obvious immediately.
 */
@Component
public class RecordedNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedNotificationAdapter.class);
    private static final String RECORDED_PROVIDER = "recorded";

    private final NotificationIntentRepository intents;
    private final Clock clock;
    private final String configuredProvider;

    RecordedNotificationAdapter(NotificationIntentRepository intents, Clock clock,
            @Value("${sfl.fleet.notification.provider:recorded}") String configuredProvider) {
        this.intents = intents;
        this.clock = clock;
        this.configuredProvider = configuredProvider;
    }

    @Override
    @Transactional
    public void notifyAssignee(SiteCode siteScope, String assignee, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        requireSupportedProvider();
        if (assignee == null || assignee.isBlank()) {
            log.warn("Fleet notification {} for {} has no assignee; nothing recorded", kind, subjectReference);
            return;
        }
        record(siteScope, NotificationIntentEntity.RECIPIENT_USER, assignee, kind, subjectReference, context);
    }

    @Override
    @Transactional
    public void notifyRole(SiteCode siteScope, SflRole role, NotificationKind kind, String subjectReference,
            Map<String, String> context) {
        requireSupportedProvider();
        record(siteScope, NotificationIntentEntity.RECIPIENT_ROLE, role.name(), kind, subjectReference, context);
    }

    private void record(SiteCode siteScope, String recipientType, String recipient, NotificationKind kind,
            String subjectReference, Map<String, String> context) {
        intents.save(new NotificationIntentEntity(UUID.randomUUID(), siteScope.value(), recipientType, recipient,
                kind, subjectReference, CanonicalJson.write(context == null ? Map.of() : context), clock.instant()));
        log.info("Fleet notification recorded: kind={} recipientType={} recipient={} subject={} site={}",
                kind, recipientType, recipient, subjectReference, siteScope.value());
    }

    private void requireSupportedProvider() {
        if (!RECORDED_PROVIDER.equalsIgnoreCase(configuredProvider == null
                ? RECORDED_PROVIDER
                : configuredProvider.strip().toLowerCase(Locale.ROOT))) {
            throw new IntegrationConfigurationNotFoundException(Map.of(
                    "capability", "fleet-notification",
                    "configuredProvider", String.valueOf(configuredProvider),
                    "reason", "No adapter is registered for this notification provider"));
        }
    }
}
