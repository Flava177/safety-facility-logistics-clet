package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.emergencynotification.domain.model.Acknowledgement;
import gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup;
import gh.edu.clet.sfl.emergencynotification.domain.model.DeliveryReceipt;
import gh.edu.clet.sfl.emergencynotification.domain.model.DrillRun;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordLifecycle;
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

    /**
     * A page of emergency records.
     *
     * <p>Shaped like the fleet {@code PageResponse}, the fuel {@code FuelPage} and the dispatch
     * {@code DispatchPage}, so every SFL collection pages the same way from an operator's point of
     * view — but declared here rather than imported from the API layer, because the port must not
     * depend on a transport type.
     *
     * <p>{@code sort} is echoed back because the caller may have asked for a default: a client that
     * cannot see which ordering it got cannot tell a stable page from a shifting one.
     */
    record EmergencyPage<T>(List<T> content, int page, int size, long totalElements, int totalPages, String sort) {
        public static <T> EmergencyPage<T> of(List<T> content, int page, int size, long totalElements, String sort) {
            int pages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
            return new EmergencyPage<>(content, page, size, totalElements, pages, sort);
        }
        public static <T> EmergencyPage<T> empty(int page, int size, String sort) {
            return new EmergencyPage<>(List.of(), page, size, 0L, 0, sort);
        }
    }

    /**
     * Paging and ordering, normalised.
     *
     * <p>{@code sort} is a key from the resource's own allow-list, never raw SQL — the adapter maps
     * it to a column plus a deterministic tiebreak on {@code id}, because a page over rows that
     * share a sort value will otherwise skip or repeat records between requests.
     */
    record Paging(int page, int size, String sort) {
        public static final int MAX_SIZE = 200;
        public Paging {
            page = Math.max(page, 0);
            size = size <= 0 ? 25 : Math.min(size, MAX_SIZE);
        }
        public int offset() {
            return page * size;
        }
    }

    /** Filters {@code GET /api/v1/emergency/activations} accepts. Any field may be null. */
    record ActivationQuery(List<String> sites, NotificationActivation.Status status, NotificationActivation.Mode mode,
            Priority priority, String incidentReference, Boolean openOnly, Boolean liveOnly,
            Boolean afterActionOutstanding, UUID scenarioId, UUID templateId, Instant from, Instant to,
            Paging paging) {
    }

    /** Filters the four master-data registers accept. They share a shape because they share a table shape. */
    record RecordQuery(List<String> sites, String search, RecordLifecycle lifecycle, Boolean breakGlassEligible,
            Paging paging) {
    }

    /** Filters {@code GET /api/v1/emergency/drills} accepts. */
    record DrillQuery(List<String> sites, DrillRun.Status status, UUID scenarioId, Instant from, Instant to,
            Paging paging) {
    }

    /** One recorded transition of an activation. Written on every change; readable since gap 4 closed. */
    record ActivationHistoryEntry(UUID id, UUID activationId, String fromStatus, String toStatus, String action,
            String actor, String comment, Instant occurredAt, String correlationId) {
    }

    // Templates
    NotificationTemplate saveTemplate(NotificationTemplate t);
    Optional<NotificationTemplate> findTemplate(UUID id);
    Optional<NotificationTemplate> findTemplateByCode(String siteCode, String templateCode);
    EmergencyPage<NotificationTemplate> findTemplates(RecordQuery query);

    // Scenarios
    EmergencyScenario saveScenario(EmergencyScenario s);
    Optional<EmergencyScenario> findScenario(UUID id);
    Optional<EmergencyScenario> findScenarioByCode(String siteCode, String scenarioCode);
    EmergencyPage<EmergencyScenario> findScenarios(RecordQuery query);

    // Audience groups
    AudienceGroup saveAudienceGroup(AudienceGroup a);
    Optional<AudienceGroup> findAudienceGroup(UUID id);
    Optional<RecipientZone> findZone(UUID id);
    Optional<AudienceGroup> findAudienceGroupByCode(String siteCode, String groupCode);
    EmergencyPage<AudienceGroup> findAudienceGroups(RecordQuery query);

    // Recipient zones
    RecipientZone saveRecipientZone(RecipientZone z);
    Optional<RecipientZone> findRecipientZone(UUID id);
    Optional<RecipientZone> findRecipientZoneByCode(String siteCode, String zoneCode);
    EmergencyPage<RecipientZone> findRecipientZones(RecordQuery query);

    // Activations
    NotificationActivation saveActivation(NotificationActivation a);
    Optional<NotificationActivation> findActivation(UUID id);
    EmergencyPage<NotificationActivation> findActivations(ActivationQuery query);
    void saveActivationHistory(UUID activationId, String fromStatus, String toStatus, String action, String actor,
            String comment, Instant occurredAt, String correlationId);
    /** The activation's recorded transitions, oldest first. Closes gap 4 — written since day one, read by nothing. */
    List<ActivationHistoryEntry> findActivationHistory(UUID activationId);

    // Channels
    NotificationChannel saveChannel(NotificationChannel c);
    List<NotificationChannel> findChannels(UUID activationId);
    Optional<NotificationChannel> findChannel(UUID activationId, gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType type);

    // Delivery receipts (idempotent)
    DeliveryReceipt saveReceipt(DeliveryReceipt r);
    Optional<DeliveryReceipt> findReceipt(UUID activationId, String provider, String providerMessageId);
    List<DeliveryReceipt> findReceipts(UUID activationId);
    /** Every acknowledgement for the activation. Closes gap 8 alongside {@link #findReceipts(UUID)}. */
    List<Acknowledgement> findAcknowledgements(UUID activationId);

    // Acknowledgements (idempotent)
    Acknowledgement saveAcknowledgement(Acknowledgement a);
    Optional<Acknowledgement> findAcknowledgement(UUID activationId, String recipientRef);
    long countAcknowledgements(UUID activationId);

    // Drills
    DrillRun saveDrill(DrillRun d);
    Optional<DrillRun> findDrill(UUID id);
    EmergencyPage<DrillRun> findDrills(DrillQuery query);

    // Dashboard read model
    Map<String, Object> dashboardCounts(List<String> sites, String site);
    /** Counts split by a dimension — channel, priority, mode or status. Closes gap 12. */
    Map<String, Map<String, Long>> dashboardBreakdown(List<String> sites, String site);
    void saveDashboardSnapshot(String scopeKey, String siteCode, Instant generatedAt, boolean stale,
            Map<String, Object> counts, Instant sourceUpdatedAt, String warnings);

    // Scheduled-sweep support
    List<UUID> findActivationsForAckEscalation(String siteCode, Instant activatedBefore, int limit);
    List<String> activeSites();
}
