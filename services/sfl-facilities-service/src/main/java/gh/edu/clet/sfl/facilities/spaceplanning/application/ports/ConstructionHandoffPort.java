package gh.edu.clet.sfl.facilities.spaceplanning.application.ports;

import java.util.List;
import java.util.UUID;

/**
 * S158's side of the hand-off to S176 Construction Project Management - SRS-SFL-S158-01/-04.
 *
 * <p>S158's own port, over S176's published {@code ConstructionProjectIntake} contract, implemented by
 * the one adapter in {@code infrastructure.integration} that names the contract. S158 therefore never
 * depends on S176's internals, and its tests can use a double.
 *
 * <p>An unavailable S176 (the contract's scaffold throws in a build without S176) surfaces as
 * {@link ConstructionUnavailableException}, which the application translates to the
 * {@code SPACE_CONSTRUCTION_INTAKE_UNAVAILABLE} error state and rolls the commit back: a physical-works
 * commit with no project behind it would be a plan waiting for a handover that can never come.
 */
public interface ConstructionHandoffPort {

    ProposedProject propose(Proposal proposal);

    /**
     * @param spaceChangeRequestId the S158-04 request, when the hand-off comes from one
     * @param committedScenarioId the scenario the works will realise, when one exists - S176 confirms it
     *        at handover through {@code ScenarioHandover}
     */
    record Proposal(UUID spaceChangeRequestId, String siteCode, String title, String scope, String requestingUnit,
            String justification, UUID committedScenarioId, List<UUID> roomIds, String requestedBy) {
        public Proposal {
            roomIds = roomIds == null ? List.of() : List.copyOf(roomIds);
        }
    }

    record ProposedProject(UUID projectId, String projectReference, String status) {
    }

    class ConstructionUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ConstructionUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
