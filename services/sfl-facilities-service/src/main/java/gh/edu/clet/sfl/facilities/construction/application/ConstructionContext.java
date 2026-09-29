package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What every S176 service shares: the repository, the authorisation gate, the audit trail, the outbox
 * and the clock, with the few rules that are the same everywhere in the module.
 *
 * <h2>The per-record rule: a project manager manages their own projects</h2>
 *
 * An actor whose only role is {@link SflRole#CONSTRUCTION_PROJECT_MANAGER} may change a project only
 * if they are its project manager - or, for a PROPOSED project that has none yet, by registering it
 * and so becoming it. Reading is site-wide: a project manager needs the pipeline to plan around. The
 * same narrowing shape as S159's requester rule, applied to writes, where it matters here: one project
 * manager revising another's baseline or linking permits to another's job is exactly the unowned edit
 * the S176-01 approval gate is meant to make visible.
 */
@Component
public class ConstructionContext {

    private final ConstructionRepository repository;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final ConstructionConfiguration configuration;
    private final Clock clock;

    public ConstructionContext(ConstructionRepository repository, FacilitiesAuthorization authorization,
            AuditPort audit, ServiceOutbox outbox, ConstructionConfiguration configuration, Clock clock) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.configuration = configuration;
        this.clock = clock;
    }

    public ConstructionRepository repository() {
        return repository;
    }

    public FacilitiesAuthorization authorization() {
        return authorization;
    }

    public AuditPort audit() {
        return audit;
    }

    public ConstructionConfiguration configuration() {
        return configuration;
    }

    public Instant now() {
        return clock.instant();
    }

    /** Today in UTC, which is Ghana's civil time: expiries and liability periods are dates, not instants. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public ConstructionProject requireProject(UUID id) {
        return repository.findProject(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Construction project", id));
    }

    public Contractor requireContractor(UUID id) {
        return repository.findContractor(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Contractor", id));
    }

    /** Permission and site, then the project-manager narrowing described on the class. */
    public void requireManage(ActorContext actor, ConstructionProject project, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_PROJECT_MANAGE, project.siteCode(), channel,
                "ConstructionProject", project.id().toString());
        if (onlyProjectManager(actor) && project.projectManagerId() != null
                && !project.projectManagerId().equals(actor.actorId())) {
            audit.recordDenial(actor, channel, "ConstructionProject", project.id().toString(), project.siteCode(),
                    "A construction project manager may manage only the projects they are project manager of");
            throw new FacilitiesException.UnauthorizedScopeException(
                    "You may only manage construction projects you are the project manager of.");
        }
    }

    public void requireRead(ActorContext actor, String siteCode, SourceChannel channel, String type, String id) {
        authorization.require(actor, SflPermission.FACILITIES_PROJECT_READ, siteCode, channel, type, id);
    }

    boolean onlyProjectManager(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        return roles.contains(SflRole.CONSTRUCTION_PROJECT_MANAGER)
                && roles.stream().allMatch(role -> role == SflRole.CONSTRUCTION_PROJECT_MANAGER);
    }

    public void publish(String eventType, String aggregateType, UUID aggregateId, String siteCode,
            ActorContext actor, Map<String, Object> payload) {
        outbox.record(eventType, 1, aggregateType, aggregateId, siteCode, actor.correlationId(), actor.actorId(),
                payload);
    }
}
