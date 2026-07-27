package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.emergencynotification.domain.model.Acknowledgement;
import gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup;
import gh.edu.clet.sfl.emergencynotification.domain.model.DeliveryReceipt;
import gh.edu.clet.sfl.emergencynotification.domain.model.DrillRun;
import gh.edu.clet.sfl.emergencynotification.domain.model.EmergencyScenario;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationChannel;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecipientZone;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for S174 domain aggregates and read models. Exposes domain records, never JDBC types. */
public interface EmergencyRepository {

    // Templates
    NotificationTemplate saveTemplate(NotificationTemplate t);
    Optional<NotificationTemplate> findTemplate(UUID id);
    Optional<NotificationTemplate> findTemplateByCode(String siteCode, String templateCode);
    List<NotificationTemplate> findTemplates(List<String> sites, int limit);

    // Scenarios
    EmergencyScenario saveScenario(EmergencyScenario s);
    Optional<EmergencyScenario> findScenario(UUID id);
    Optional<EmergencyScenario> findScenarioByCode(String siteCode, String scenarioCode);
    List<EmergencyScenario> findScenarios(List<String> sites, int limit);

    // Audience groups
    AudienceGroup saveAudienceGroup(AudienceGroup a);
    Optional<AudienceGroup> findAudienceGroup(UUID id);
    Optional<AudienceGroup> findAudienceGroupByCode(String siteCode, String groupCode);
    List<AudienceGroup> findAudienceGroups(List<String> sites, int limit);

    // Recipient zones
    RecipientZone saveRecipientZone(RecipientZone z);
    Optional<RecipientZone> findRecipientZone(UUID id);
    Optional<RecipientZone> findRecipientZoneByCode(String siteCode, String zoneCode);
    List<RecipientZone> findRecipientZones(List<String> sites, int limit);

    // Activations
    NotificationActivation saveActivation(NotificationActivation a);
    Optional<NotificationActivation> findActivation(UUID id);
    List<NotificationActivation> findActivations(List<String> sites, NotificationActivation.Status status, int limit);
    void saveActivationHistory(UUID activationId, String fromStatus, String toStatus, String action, String actor,
            String comment, Instant occurredAt, String correlationId);

    // Channels
    NotificationChannel saveChannel(NotificationChannel c);
    List<NotificationChannel> findChannels(UUID activationId);
    Optional<NotificationChannel> findChannel(UUID activationId, gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType type);

    // Delivery receipts (idempotent)
    DeliveryReceipt saveReceipt(DeliveryReceipt r);
    Optional<DeliveryReceipt> findReceipt(UUID activationId, String provider, String providerMessageId);
    List<DeliveryReceipt> findReceipts(UUID activationId);

    // Acknowledgements (idempotent)
    Acknowledgement saveAcknowledgement(Acknowledgement a);
    Optional<Acknowledgement> findAcknowledgement(UUID activationId, String recipientRef);
    long countAcknowledgements(UUID activationId);

    // Drills
    DrillRun saveDrill(DrillRun d);
    Optional<DrillRun> findDrill(UUID id);
    List<DrillRun> findDrills(List<String> sites, int limit);

    // Dashboard read model
    Map<String, Object> dashboardCounts(List<String> sites, String site);
    void saveDashboardSnapshot(String scopeKey, String siteCode, Instant generatedAt, boolean stale,
            Map<String, Object> counts, Instant sourceUpdatedAt, String warnings);

    // Scheduled-sweep support
    List<UUID> findActivationsForAckEscalation(String siteCode, Instant activatedBefore, int limit);
    List<String> activeSites();
}
