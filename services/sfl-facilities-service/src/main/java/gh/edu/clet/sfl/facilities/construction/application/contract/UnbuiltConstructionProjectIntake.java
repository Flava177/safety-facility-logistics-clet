package gh.edu.clet.sfl.facilities.construction.application.contract;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Build scaffolding until the S176 module lands; the S176 build deletes this file. */
@Component
class UnbuiltConstructionProjectIntake implements ConstructionProjectIntake {

    @Override
    public ProposedProject proposeFromSpaceChange(SpaceChangeProposal proposal) {
        throw new IllegalStateException("S176 construction project intake is not built in this build.");
    }

    @Override
    public Optional<ProposedProject> find(UUID projectId) {
        return Optional.empty();
    }
}
