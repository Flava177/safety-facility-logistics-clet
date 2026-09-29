package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.spaceplanning.application.contract.ScenarioHandover;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioUncommittedException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S158's implementation of the {@link ScenarioHandover} contract S176 calls at handover - SRS-SFL-S176-04.
 *
 * <p>"Handover updates S152 and, where the project committed an S158 scenario, confirms that commit." S176
 * says the works are done; S158 decides what that means - apply the scenario's allocations to the S152
 * register and move it to {@code HANDED_OVER} - in {@link SpaceScenarioService#confirmHandover}.
 *
 * <h2>Who the change is recorded against</h2>
 *
 * S176 has already authorised its own caller for its own act ({@code FACILITIES_PROJECT_HANDOVER}), and
 * that person does not hold, and should not need, S158's commit permission - the same reasoning as
 * {@code AutomatedWorkOrderIntake}. The register change therefore runs as a service account scoped to the
 * scenario's site only, whose subject is the confirming person, so the audit trail still answers "who"
 * and an auditor can tell the row from a planner's own commit ({@code serviceAccount = true}).
 *
 * <h2>No authorisation on {@link #find}</h2>
 *
 * An in-process contract returning an id, a name and a status - nothing a project manager holding
 * {@code FACILITIES_SPACE_PLAN_READ} (which every S176 role has) should not see. Row-level security still
 * narrows it to the transaction's scope underneath, as for {@code BookingUtilisationReader}.
 */
@Service
public class ScenarioHandoverService implements ScenarioHandover {

    private final SpacePlanningRepository repository;
    private final SpaceScenarioService scenarios;

    public ScenarioHandoverService(SpacePlanningRepository repository, SpaceScenarioService scenarios) {
        this.repository = repository;
        this.scenarios = scenarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScenarioSummary> find(UUID scenarioId) {
        return repository.findScenario(scenarioId).map(ScenarioHandoverService::summary);
    }

    @Override
    @Transactional(noRollbackFor = ScenarioUncommittedException.class)
    public ScenarioSummary confirmHandover(UUID scenarioId, UUID projectId, String projectReference,
            String confirmedBy) {
        Objects.requireNonNull(scenarioId, "scenarioId is required");
        Objects.requireNonNull(projectId, "projectId is required");
        if (confirmedBy == null || confirmedBy.isBlank()) {
            throw new IllegalArgumentException("confirmedBy is required: a handover is a named act");
        }
        AllocationScenario scenario = scenarios.requireScenario(scenarioId);
        SiteScopedPrincipal principal = new SiteScopedPrincipal(confirmedBy.strip(),
                "S176 handover (" + (projectReference == null ? projectId : projectReference) + ")",
                Set.of(SflRole.SFL_ADMIN), Set.of(scenario.siteCode()), true);
        ActorContext actor = new ActorContext(principal, "s176-handover:" + projectId);
        return summary(scenarios.confirmHandover(scenarioId, projectId, projectReference, actor));
    }

    static ScenarioSummary summary(AllocationScenario scenario) {
        return new ScenarioSummary(scenario.id(), scenario.siteCode(), scenario.name(),
                Status.valueOf(scenario.status().name()), scenario.linkedProjectId());
    }
}
