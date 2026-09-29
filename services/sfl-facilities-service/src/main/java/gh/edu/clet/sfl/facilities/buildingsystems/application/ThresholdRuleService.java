package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleConflict;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.RuleEvaluationPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Threshold and fault-condition rules - SRS-SFL-S156-02.
 *
 * <h2>Who may do what</h2>
 *
 * <ul>
 *   <li>Create, revise, re-enable: {@code FACILITIES_BMS_RULE_MANAGE} - the Facilities Engineer of the user
 *       story.</li>
 *   <li>Disable: {@code FACILITIES_BMS_RULE_OVERRIDE}, a reason, and a named accountable owner. The engineer
 *       who writes rules does not hold it (see {@code FacilitiesPermissionMatrix.grantPhaseTwo}): the author
 *       of a rule switching it off alone is exactly the silent disable the validation rule forbids.</li>
 * </ul>
 *
 * <h2>Every change is a version</h2>
 *
 * <p>Each command writes a new version row and stamps the previous one superseded, audits it
 * ({@code BMS_RULE_CREATED} / {@code _REVISED} / {@code _DISABLED_BY_OVERRIDE} / {@code _ENABLED}) with the
 * before and after, and publishes {@code bms-rule-changed}. Revising takes the version the author was looking
 * at, so two engineers editing the same rule cannot silently overwrite each other.
 *
 * <h2>Conflicts are logged, not refused</h2>
 *
 * <p>After every change that leaves a rule active, {@link RuleEvaluationPolicy#conflictsOf} is run against
 * the site's other active rules and each overlap is recorded once, audited as
 * {@code BMS_RULE_CONFLICT_DETECTED}, and returned to the author. Evaluation applies the stricter limit
 * regardless - see the policy.
 */
@Service
public class ThresholdRuleService {

    private final BuildingSystemsRepository repository;
    private final BuildingSystemsConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public ThresholdRuleService(BuildingSystemsRepository repository, BuildingSystemsConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** A rule change and the conflicts it surfaced. */
    public record RuleChange(ThresholdRule rule, List<RuleConflict> conflicts) {
    }

    @Transactional
    public RuleChange create(BuildingSystemsCommands.CreateRule command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_BMS_RULE_MANAGE, site, command.channel(),
                "BmsThresholdRule", "new");
        ThresholdRule rule = repository.saveRule(ThresholdRule.create(UUID.randomUUID(), UUID.randomUUID(), site,
                command.name(), command.quantity(), command.systemType(), command.deviceId(), command.buildingCode(),
                command.roomId(), command.condition(), command.lowerLimit(), command.upperLimit(), command.codes(),
                command.debounce() == null ? configuration.defaultDebounce(site) : command.debounce(),
                command.priority(), command.reason(), actor.actorId(), clock.instant(), command.channel(),
                actor.correlationId()));
        return changed(AuditAction.BMS_RULE_CREATED, null, rule, actor, command.channel());
    }

    @Transactional
    public RuleChange revise(BuildingSystemsCommands.ReviseRule command) {
        ActorContext actor = command.actor();
        ThresholdRule current = requireCurrent(command.ruleId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_RULE_MANAGE, current.siteCode(), command.channel(),
                "BmsThresholdRule", current.ruleId().toString());
        if (command.expectedRuleVersion() != null && command.expectedRuleVersion() != current.ruleVersion()) {
            throw new FacilitiesException.VersionConflictException("BmsThresholdRule", current.ruleId(),
                    command.expectedRuleVersion(), current.ruleVersion());
        }
        Instant now = clock.instant();
        ThresholdRule next = current.revise(UUID.randomUUID(), command.name() == null ? current.name() : command.name(),
                command.systemType(), command.deviceId(), command.buildingCode(), command.roomId(),
                command.condition() == null ? current.condition() : command.condition(), command.lowerLimit(),
                command.upperLimit(), command.codes(),
                command.debounce() == null ? current.debounce() : command.debounce(),
                command.priority() == null ? current.priority() : command.priority(), command.reason(),
                actor.actorId(), now, command.channel(), actor.correlationId());
        return supersede(current, next, AuditAction.BMS_RULE_REVISED, actor, command.channel());
    }

    /** The audited override. See the class comment for who may and what it demands. */
    @Transactional
    public RuleChange disable(BuildingSystemsCommands.DisableRule command) {
        ActorContext actor = command.actor();
        ThresholdRule current = requireCurrent(command.ruleId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_RULE_OVERRIDE, current.siteCode(),
                command.channel(), "BmsThresholdRule", current.ruleId().toString());
        ThresholdRule next = current.disable(UUID.randomUUID(), command.reason(), command.accountableOwner(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId());
        return supersede(current, next, AuditAction.BMS_RULE_DISABLED_BY_OVERRIDE, actor, command.channel());
    }

    /** Re-enabling restores protection, so it needs only the authoring permission - or the override one. */
    @Transactional
    public RuleChange enable(BuildingSystemsCommands.EnableRule command) {
        ActorContext actor = command.actor();
        ThresholdRule current = requireCurrent(command.ruleId());
        SflPermission permission = authorization.has(actor, SflPermission.FACILITIES_BMS_RULE_MANAGE)
                ? SflPermission.FACILITIES_BMS_RULE_MANAGE : SflPermission.FACILITIES_BMS_RULE_OVERRIDE;
        authorization.require(actor, permission, current.siteCode(), command.channel(), "BmsThresholdRule",
                current.ruleId().toString());
        ThresholdRule next = current.enable(UUID.randomUUID(), command.reason(), actor.actorId(), clock.instant(),
                command.channel(), actor.correlationId());
        return supersede(current, next, AuditAction.BMS_RULE_ENABLED, actor, command.channel());
    }

    @Transactional(readOnly = true)
    public List<ThresholdRule> rules(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsThresholdRule", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BmsThresholdRule");
        return authorization.filterBySite(actor, repository.findCurrentRules(siteCode), ThresholdRule::siteCode);
    }

    /** Every version, oldest first - the answer to "what was this threshold on the day of the incident". */
    @Transactional(readOnly = true)
    public List<ThresholdRule> history(UUID ruleId, ActorContext actor, SourceChannel channel) {
        List<ThresholdRule> versions = repository.findRuleVersions(ruleId);
        if (versions.isEmpty()) {
            throw new FacilitiesException.RecordNotFoundException("BmsThresholdRule", ruleId);
        }
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, versions.get(0).siteCode(), channel,
                "BmsThresholdRule", ruleId.toString());
        return versions;
    }

    @Transactional(readOnly = true)
    public List<RuleConflict> conflicts(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsRuleConflict", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BmsRuleConflict");
        return authorization.filterBySite(actor, repository.findConflicts(siteCode), RuleConflict::siteCode);
    }

    private RuleChange supersede(ThresholdRule current, ThresholdRule next, AuditAction action, ActorContext actor,
            SourceChannel channel) {
        repository.saveRule(current.supersede(next.metadata().createdAt()));
        return changed(action, current, repository.saveRule(next), actor, channel);
    }

    private RuleChange changed(AuditAction action, ThresholdRule before, ThresholdRule after, ActorContext actor,
            SourceChannel channel) {
        audit.record(actor, channel, action, "BmsThresholdRule", after.ruleId().toString(), after.siteCode(), before,
                after);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", after.ruleId().toString());
        payload.put("ruleVersion", after.ruleVersion());
        payload.put("versionId", after.id().toString());
        payload.put("siteCode", after.siteCode());
        payload.put("change", action.name());
        payload.put("kind", after.quantity().name());
        payload.put("condition", after.condition().name());
        payload.put("enabled", after.enabled());
        payload.put("accountableOwner", after.accountableOwner());
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.RULE_CHANGED, "BmsThresholdRule", after.ruleId(),
                after.siteCode(), actor, payload);
        return new RuleChange(after, logConflicts(after, actor, channel));
    }

    private List<RuleConflict> logConflicts(ThresholdRule rule, ActorContext actor, SourceChannel channel) {
        Instant now = clock.instant();
        return RuleEvaluationPolicy.conflictsOf(rule, repository.findCurrentRules(rule.siteCode())).stream()
                .filter(conflict -> !repository.conflictLogged(rule.ruleId(), conflict.second().ruleId(),
                        conflict.stricter().ruleId()))
                .map(conflict -> {
                    RuleConflict logged = repository.saveConflict(new RuleConflict(UUID.randomUUID(), rule.siteCode(),
                            rule.ruleId(), conflict.second().ruleId(), conflict.stricter().ruleId(), rule.quantity(),
                            conflict.detail(), now,
                            RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId())));
                    audit.record(actor, channel, AuditAction.BMS_RULE_CONFLICT_DETECTED, "BmsRuleConflict",
                            logged.id().toString(), logged.siteCode(), null, logged);
                    return logged;
                })
                .toList();
    }

    private ThresholdRule requireCurrent(UUID ruleId) {
        return repository.findCurrentRule(ruleId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("BmsThresholdRule", ruleId));
    }
}
