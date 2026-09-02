package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a {@link SecurityIncident} - SRS §D.9, following the same {@code ALLOWED}-map shape as
 * {@code visitor.domain.model.VisitStatus} / facilities' {@code WorkOrderStatus}.
 *
 * <h2>Why emergency escalation is not a status node</h2>
 *
 * D.9 hard rule 2 ("an emergency-rated incident triggers the command/emergency path automatically")
 * reads at first like a branch state, but step 2 also says severity is "revisable during
 * investigation" - an incident can be re-rated emergency, or down from it, without leaving whatever
 * lifecycle stage it is already in. Modelling escalation as a status would force a choice between
 * "still triaging" and "now escalated" that the SRS does not actually make; {@link
 * SecurityIncident#emergencyEscalated()} is a sticky fact recorded alongside the status instead - once
 * true it stays true (an escalation that happened cannot un-happen), independent of which of these
 * three stages the incident is in.
 */
public enum IncidentStatus {

    /** Reported and awaiting or undergoing severity/risk rating. */
    TRIAGE,
    /** An investigator has opened the case; findings, evidence and CAPA accumulate here. */
    INVESTIGATING,
    /** Closed. Terminal - only reachable once no mandatory corrective action remains open. */
    CLOSED;

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED = Map.of(
            TRIAGE, EnumSet.of(INVESTIGATING),
            INVESTIGATING, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(IncidentStatus.class));

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean canTransitionTo(IncidentStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public IncidentStatus transitionTo(IncidentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IncidentException(IncidentErrorCode.INCIDENT_INVALID_STATE_TRANSITION,
                    Map.of("from", name(), "to", target == null ? "" : target.name()));
        }
        return target;
    }
}
