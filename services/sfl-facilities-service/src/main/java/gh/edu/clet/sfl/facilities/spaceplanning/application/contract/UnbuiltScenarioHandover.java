package gh.edu.clet.sfl.facilities.spaceplanning.application.contract;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Build scaffolding until the S158 module lands; the S158 build deletes this file. */
@Component
class UnbuiltScenarioHandover implements ScenarioHandover {

    @Override
    public Optional<ScenarioSummary> find(UUID scenarioId) {
        return Optional.empty();
    }

    @Override
    public ScenarioSummary confirmHandover(UUID scenarioId, UUID projectId, String projectReference,
            String confirmedBy) {
        throw new IllegalStateException("S158 scenario handover is not built in this build.");
    }
}
