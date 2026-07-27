package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.EmergencyScenario;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecipientZone;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordLifecycle;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordMetadata;
import gh.edu.clet.sfl.emergencynotification.domain.model.SiteCode;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S174-01: create and maintain the emergency notification operational records (site-scoped). */
@Service
public class EmergencyRecordsService {

    private final EmergencyRepository repository;
    private final EmergencyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public EmergencyRecordsService(EmergencyRepository repository, EmergencyAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record CreateTemplate(String siteCode, String templateCode, String title, String body,
            List<ChannelType> channels, boolean breakGlassEligible, ActorContext actor, SourceChannel channel) {}

    public record CreateScenario(String siteCode, String scenarioCode, String name, Priority priority,
            UUID defaultTemplateId, boolean breakGlassEligible, ActorContext actor, SourceChannel channel) {}

    public record CreateAudienceGroup(String siteCode, String groupCode, String name, String directoryReference,
            int recipientCount, ActorContext actor, SourceChannel channel) {}

    public record CreateRecipientZone(String siteCode, String zoneCode, String name, String locationReference,
            ActorContext actor, SourceChannel channel) {}

    @Transactional
    public NotificationTemplate createTemplate(CreateTemplate c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_TEMPLATE_MANAGE, site.value(), "NotificationTemplate", null);
        String code = number(c.templateCode(), "TPL");
        repository.findTemplateByCode(site.value(), code).filter(NotificationTemplate::active)
                .ifPresent(existing -> { throw duplicate(code); });
        var template = new NotificationTemplate(UUID.randomUUID(), code, site, c.title(), c.body(), c.channels(),
                c.breakGlassEligible(), RecordLifecycle.ACTIVE, meta(c.actor(), c.channel()));
        var saved = repository.saveTemplate(template);
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "NotificationTemplate", saved.id().toString(),
                null, saved, null);
        events.publish(EmergencyEventType.EMERGENCY_TEMPLATE_CREATED, "NotificationTemplate", saved.id().toString(),
                site.value(), c.actor(), Map.of("templateId", saved.id(), "templateCode", saved.templateCode(),
                        "breakGlassEligible", saved.breakGlassEligible()));
        return saved;
    }

    @Transactional
    public EmergencyScenario createScenario(CreateScenario c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_SCENARIO_MANAGE, site.value(), "EmergencyScenario", null);
        String code = number(c.scenarioCode(), "SCN");
        repository.findScenarioByCode(site.value(), code).filter(EmergencyScenario::active)
                .ifPresent(existing -> { throw duplicate(code); });
        var scenario = new EmergencyScenario(UUID.randomUUID(), code, site, c.name(), c.priority(),
                c.defaultTemplateId(), c.breakGlassEligible(), RecordLifecycle.ACTIVE, meta(c.actor(), c.channel()));
        var saved = repository.saveScenario(scenario);
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "EmergencyScenario", saved.id().toString(), null,
                saved, null);
        return saved;
    }

    @Transactional
    public AudienceGroup createAudienceGroup(CreateAudienceGroup c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_AUDIENCE_MANAGE, site.value(), "AudienceGroup", null);
        String code = number(c.groupCode(), "AUD");
        repository.findAudienceGroupByCode(site.value(), code).filter(AudienceGroup::active)
                .ifPresent(existing -> { throw duplicate(code); });
        var group = new AudienceGroup(UUID.randomUUID(), code, site, c.name(), c.directoryReference(),
                Math.max(c.recipientCount(), 0), RecordLifecycle.ACTIVE, meta(c.actor(), c.channel()));
        var saved = repository.saveAudienceGroup(group);
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "AudienceGroup", saved.id().toString(), null,
                saved, null);
        return saved;
    }

    @Transactional
    public RecipientZone createRecipientZone(CreateRecipientZone c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_AUDIENCE_MANAGE, site.value(), "RecipientZone", null);
        String code = number(c.zoneCode(), "ZON");
        repository.findRecipientZoneByCode(site.value(), code).filter(RecipientZone::active)
                .ifPresent(existing -> { throw duplicate(code); });
        var zone = new RecipientZone(UUID.randomUUID(), code, site, c.name(), c.locationReference(),
                RecordLifecycle.ACTIVE, meta(c.actor(), c.channel()));
        var saved = repository.saveRecipientZone(zone);
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "RecipientZone", saved.id().toString(), null,
                saved, null);
        return saved;
    }

    // ---- queries ---------------------------------------------------------------------------------

    public List<NotificationTemplate> templates(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_TEMPLATE_READ, site, "NotificationTemplate", null);
        return repository.findTemplates(List.of(SiteCode.of(site).value()), 200);
    }

    public NotificationTemplate template(UUID id, ActorContext actor) {
        var t = repository.findTemplate(id).orElseThrow(() -> EmergencyException.notFound("NotificationTemplate", id));
        access.require(actor, SflPermission.EMERGENCY_TEMPLATE_READ, t.siteCode().value(), "NotificationTemplate",
                id.toString());
        return t;
    }

    public List<EmergencyScenario> scenarios(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_SCENARIO_READ, site, "EmergencyScenario", null);
        return repository.findScenarios(List.of(SiteCode.of(site).value()), 200);
    }

    public List<AudienceGroup> audienceGroups(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_AUDIENCE_READ, site, "AudienceGroup", null);
        return repository.findAudienceGroups(List.of(SiteCode.of(site).value()), 200);
    }

    public List<RecipientZone> recipientZones(String site, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_AUDIENCE_READ, site, "RecipientZone", null);
        return repository.findRecipientZones(List.of(SiteCode.of(site).value()), 200);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private RecordMetadata meta(ActorContext actor, SourceChannel channel) {
        Instant now = clock.instant();
        return RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId());
    }

    private static SiteCode requireSite(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_MISSING_SITE_SCOPE);
        }
        return SiteCode.of(siteCode);
    }

    private static String number(String provided, String prefix) {
        return provided == null || provided.isBlank() ? EmergencyNumbers.next(prefix) : provided.strip();
    }

    private static EmergencyException duplicate(String code) {
        return new EmergencyException(EmergencyErrorCode.EMERGENCY_DUPLICATE_IDENTIFIER, Map.of("identifier", code));
    }
}
