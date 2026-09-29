package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CcpEventsDirectoryPort;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventRiskPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The higher-risk criteria (SRS-SFL-S173-03) and the integration position of S173's dependencies.
 *
 * <p>The criteria are runtime configuration, versioned by {@link RuntimeConfigurationPort#put}, and changed
 * only by holders of {@code FACILITIES_EVENT_RISK_CATEGORY_MANAGE} - the HSE role of S173-03's user story.
 * A coordinator who could relax the criteria could exempt their own event from the assessment it
 * needs, which is the separation this keeps.
 */
@Service
public class EventRiskCriteriaService {

    private final RuntimeConfigurationPort runtime;
    private final EventLogisticsConfiguration configuration;
    private final CcpEventsDirectoryPort directory;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    public EventRiskCriteriaService(RuntimeConfigurationPort runtime, EventLogisticsConfiguration configuration,
            CcpEventsDirectoryPort directory, FacilitiesAuthorization authorization, AuditPort audit) {
        this.runtime = runtime;
        this.configuration = configuration;
        this.directory = directory;
        this.authorization = authorization;
        this.audit = audit;
    }

    /** One owning system's position in this deployment. */
    public record OwningSystemStatus(OwningSystem system, String name, boolean available, String statement) {
    }

    /** Everything S173 depends on, stated plainly - never "integrated" for something that is not. */
    public record IntegrationPosition(String ccpEvents, List<OwningSystemStatus> owningSystems, String riskAssessments) {
    }

    @Transactional(readOnly = true)
    public EventRiskPolicy.RiskCriteria criteria(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, channel, "EventRiskCriteria", "read",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "EventRiskCriteria");
        return configuration.riskCriteria(site(siteCode));
    }

    @Transactional
    public EventRiskPolicy.RiskCriteria configure(EventLogisticsCommands.ConfigureRiskCriteria command) {
        ActorContext actor = command.actor();
        String site = site(command.siteCode());
        if (site == null) {
            authorization.require(actor, SflPermission.FACILITIES_EVENT_RISK_CATEGORY_MANAGE, command.channel(),
                    "EventRiskCriteria", "platform", "*");
            authorization.requireSite(actor, "*", command.channel(), "EventRiskCriteria", "platform");
        } else {
            authorization.require(actor, SflPermission.FACILITIES_EVENT_RISK_CATEGORY_MANAGE, site, command.channel(),
                    "EventRiskCriteria", site);
        }
        EventRiskPolicy.RiskCriteria before = configuration.riskCriteria(site);
        if (command.attendanceThreshold() != null) {
            if (command.attendanceThreshold() < 0) {
                throw new FacilitiesException.ValidationFailedException(
                        "The attendance threshold cannot be negative; zero switches the marker off.");
            }
            runtime.put(EventLogisticsConfiguration.KEY_RISK_ATTENDANCE, site,
                    String.valueOf(command.attendanceThreshold()), "INTEGER",
                    "Expected attendance at or above this makes an event higher-risk (S173-03).", actor.actorId());
        }
        if (command.externalContractorsAreHigherRisk() != null) {
            runtime.put(EventLogisticsConfiguration.KEY_RISK_EXTERNAL_CONTRACTORS, site,
                    String.valueOf(command.externalContractorsAreHigherRisk()), "BOOLEAN",
                    "Events involving external contractors are higher-risk (S173-03).", actor.actorId());
        }
        if (command.temporaryStructuresAreHigherRisk() != null) {
            runtime.put(EventLogisticsConfiguration.KEY_RISK_TEMPORARY_STRUCTURES, site,
                    String.valueOf(command.temporaryStructuresAreHigherRisk()), "BOOLEAN",
                    "Events with temporary structures are higher-risk (S173-03).", actor.actorId());
        }
        if (command.higherRiskCategories() != null) {
            TreeSet<String> categories = new TreeSet<>();
            command.higherRiskCategories().stream().filter(value -> value != null && !value.isBlank())
                    .map(value -> value.strip().toUpperCase(Locale.ROOT)).forEach(categories::add);
            runtime.put(EventLogisticsConfiguration.KEY_RISK_CATEGORIES, site, String.join(",", categories), "STRING",
                    "S078 event categories that are higher-risk whatever their size (S173-03).", actor.actorId());
        }
        EventRiskPolicy.RiskCriteria after = configuration.riskCriteria(site);
        audit.record(actor, command.channel(), AuditAction.EVENT_RISK_CATEGORY_CONFIGURED, "EventRiskCriteria",
                site == null ? "platform" : site, site == null ? "*" : site, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public IntegrationPosition integration(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, channel, "EventLogisticsIntegration", "read",
                siteCode);
        String site = site(siteCode);
        List<OwningSystemStatus> systems = Arrays.stream(OwningSystem.values()).map(system -> {
            boolean available = configuration.owningSystemAvailable(system, site);
            return new OwningSystemStatus(system, system.displayName(), available, statement(system, available));
        }).toList();
        return new IntegrationPosition(directory.describe(), systems,
                "S165 Risk Assessment Library is not built. S173 keeps a projection fed by"
                        + " sfl.ssemp.risk-assessment-{published,superseded,review-lapsed,signed-off}.v1, which"
                        + " nothing publishes yet, so every higher-risk confirmation is refused (fail-closed).");
    }

    private static String statement(OwningSystem system, boolean available) {
        Map<OwningSystem, String> built = Map.of(
                OwningSystem.S159, "In-process call to S159 in this deployable.",
                OwningSystem.S153, "In-process call to S153's automated work-order intake in this deployable.",
                OwningSystem.S169, "Through S169's EventCleaningCapacity contract. Where the S169 implementation is"
                        + " absent, cleaning requests become manual coordination items.",
                OwningSystem.S172, "Phase 3, not built. Catering is recorded as manual coordination.");
        return (available ? "" : "Marked unavailable: requests become manual coordination items. ")
                + built.get(system);
    }

    private static String site(String siteCode) {
        return siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
    }
}
