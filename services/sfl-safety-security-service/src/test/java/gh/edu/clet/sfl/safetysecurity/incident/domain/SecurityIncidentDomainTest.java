package gh.edu.clet.sfl.safetysecurity.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Impact;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Likelihood;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskBand;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskRating;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Framework-free unit tests for the S163 domain aggregates and their state machines. */
class SecurityIncidentDomainTest {

    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

    private SecurityIncident reported() {
        return SecurityIncident.report(UUID.randomUUID(), "HQ", IncidentSource.REPORTED, "INC-TEST0001", false,
                "reporter-1", "reporter@example.com", "A near-miss on the loading dock.", true, "reporter-1", NOW,
                SourceChannel.WEB, "corr-1");
    }

    // --- risk rating banding ---

    @Test
    void the_default_risk_matrix_bands_low_medium_high_and_critical() {
        assertThat(new RiskRating(Likelihood.RARE, Impact.NEGLIGIBLE).band()).isEqualTo(RiskBand.LOW);
        assertThat(new RiskRating(Likelihood.POSSIBLE, Impact.MODERATE).band()).isEqualTo(RiskBand.MEDIUM);
        assertThat(new RiskRating(Likelihood.LIKELY, Impact.MODERATE).band()).isEqualTo(RiskBand.HIGH);
        assertThat(new RiskRating(Likelihood.ALMOST_CERTAIN, Impact.CATASTROPHIC).band()).isEqualTo(RiskBand.CRITICAL);
    }

    // --- report / anonymity ---

    @Test
    void an_anonymous_report_carries_no_reporter_identity() {
        SecurityIncident incident = SecurityIncident.report(UUID.randomUUID(), "HQ", IncidentSource.REPORTED,
                "INC-TEST0002", true, "should-be-dropped", null, "Anonymous near-miss.", true, "reporter-1", NOW,
                SourceChannel.WEB, "corr-1");
        assertThat(incident.anonymous()).isTrue();
        assertThat(incident.reporterId()).isNull();
    }

    @Test
    void a_new_report_starts_in_triage_with_no_severity() {
        SecurityIncident incident = reported();
        assertThat(incident.status()).isEqualTo(IncidentStatus.TRIAGE);
        assertThat(incident.severity()).isNull();
        assertThat(incident.emergencyEscalated()).isFalse();
    }

    // --- triage ---

    @Test
    void triage_does_not_change_status_but_records_severity() {
        SecurityIncident triaged = reported().triage(Severity.HIGH,
                new RiskRating(Likelihood.LIKELY, Impact.MAJOR), false, null, "hse-1", NOW, SourceChannel.WEB,
                "corr-1");
        assertThat(triaged.status()).isEqualTo(IncidentStatus.TRIAGE);
        assertThat(triaged.severity()).isEqualTo(Severity.HIGH);
        assertThat(triaged.emergencyEscalated()).isFalse();
    }

    @Test
    void an_emergency_rating_latches_emergency_escalated_permanently() {
        SecurityIncident escalated = reported().triage(Severity.EMERGENCY,
                new RiskRating(Likelihood.ALMOST_CERTAIN, Impact.CATASTROPHIC), false, null, "hse-1", NOW,
                SourceChannel.WEB, "corr-1");
        assertThat(escalated.emergencyEscalated()).isTrue();

        // Re-triaging down does not un-escalate - the escalation already happened.
        SecurityIncident downgraded = escalated.triage(Severity.LOW, new RiskRating(Likelihood.RARE, Impact.MINOR),
                false, null, "hse-1", NOW, SourceChannel.WEB, "corr-1");
        assertThat(downgraded.emergencyEscalated()).isTrue();
        assertThat(downgraded.severity()).isEqualTo(Severity.LOW);
    }

    @Test
    void a_closed_incident_cannot_be_re_triaged() {
        SecurityIncident closed = closedIncident();
        assertThatThrownBy(() -> closed.triage(Severity.LOW, null, false, null, "hse-1", NOW, SourceChannel.WEB,
                "corr-1")).isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_INVALID_STATE_TRANSITION));
    }

    // --- investigation ---

    @Test
    void an_investigation_cannot_open_before_triage() {
        SecurityIncident incident = reported();
        assertThatThrownBy(() -> incident.openInvestigation("investigator-1", null, "investigator-1", NOW,
                SourceChannel.WEB, "corr-1")).isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_TRIAGE_REQUIRED));
    }

    @Test
    void an_investigation_opens_after_triage_and_moves_to_investigating() {
        SecurityIncident triaged = triaged(reported());
        SecurityIncident investigating = triaged.openInvestigation("investigator-1", "Initial findings.",
                "investigator-1", NOW, SourceChannel.WEB, "corr-1");
        assertThat(investigating.status()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(investigating.investigatorId()).isEqualTo("investigator-1");
        assertThat(investigating.investigationNotes()).isEqualTo("Initial findings.");
    }

    @Test
    void investigation_notes_can_only_be_updated_while_investigating() {
        SecurityIncident triaged = triaged(reported());
        assertThatThrownBy(() -> triaged.updateInvestigationNotes("Too early.", "investigator-1", NOW,
                SourceChannel.WEB, "corr-1")).isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_INVALID_STATE_TRANSITION));

        SecurityIncident investigating = triaged.openInvestigation("investigator-1", null, "investigator-1", NOW,
                SourceChannel.WEB, "corr-1");
        SecurityIncident updated = investigating.updateInvestigationNotes("Root cause found.", "investigator-1",
                NOW, SourceChannel.WEB, "corr-1");
        assertThat(updated.investigationNotes()).isEqualTo("Root cause found.");
        assertThat(updated.status()).isEqualTo(IncidentStatus.INVESTIGATING);
    }

    // --- closure: the important business rule ---

    @Test
    void closure_is_refused_while_a_mandatory_corrective_action_is_open() {
        SecurityIncident investigating = investigating();
        assertThatThrownBy(() -> investigating.close("All actions verified.", 1, "investigator-1", NOW,
                SourceChannel.WEB, "corr-1")).isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_MANDATORY_CAPA_OPEN));
    }

    @Test
    void closure_succeeds_once_no_mandatory_corrective_action_remains_open() {
        SecurityIncident investigating = investigating();
        SecurityIncident closed = investigating.close("All actions verified.", 0, "investigator-1", NOW,
                SourceChannel.WEB, "corr-1");
        assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.closureNotes()).isEqualTo("All actions verified.");
        assertThat(closed.closedAt()).isEqualTo(NOW);
    }

    @Test
    void a_closed_incident_cannot_close_again() {
        SecurityIncident closed = closedIncident();
        assertThatThrownBy(() -> closed.close("Again.", 0, "investigator-1", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_INVALID_STATE_TRANSITION));
    }

    // --- CorrectiveAction lifecycle and closure-blocking ---

    @Test
    void a_mandatory_open_capa_blocks_closure_and_a_non_mandatory_one_does_not() {
        SecurityIncident incident = triaged(reported());
        CorrectiveAction mandatory = CorrectiveAction.open(UUID.randomUUID(), incident, "Fix the guardrail.",
                "owner-1", LocalDate.parse("2026-09-10"), true, "investigator-1", NOW);
        CorrectiveAction optional = CorrectiveAction.open(UUID.randomUUID(), incident, "Repaint the sign.",
                "owner-1", LocalDate.parse("2026-09-10"), false, "investigator-1", NOW);
        assertThat(mandatory.blocksClosure()).isTrue();
        assertThat(optional.blocksClosure()).isFalse();
    }

    @Test
    void a_verified_mandatory_capa_no_longer_blocks_closure() {
        SecurityIncident incident = triaged(reported());
        CorrectiveAction action = CorrectiveAction.open(UUID.randomUUID(), incident, "Fix the guardrail.",
                "owner-1", LocalDate.parse("2026-09-10"), true, "investigator-1", NOW);
        CorrectiveAction verified = action.startProgress("owner-1", NOW).verify("Re-inspected, guardrail secure.",
                "supervisor-1", NOW);
        assertThat(verified.status()).isEqualTo(gh.edu.clet.sfl.safetysecurity.incident.domain.model.CapaStatus.VERIFIED);
        assertThat(verified.blocksClosure()).isFalse();
        assertThat(verified.resolvedBy()).isEqualTo("supervisor-1");
    }

    @Test
    void a_cancelled_mandatory_capa_requires_a_reason_and_no_longer_blocks_closure() {
        SecurityIncident incident = triaged(reported());
        CorrectiveAction action = CorrectiveAction.open(UUID.randomUUID(), incident, "Fix the guardrail.",
                "owner-1", LocalDate.parse("2026-09-10"), true, "investigator-1", NOW);
        assertThatThrownBy(() -> action.cancel(null, "investigator-1", NOW))
                .isInstanceOf(IllegalArgumentException.class);

        CorrectiveAction cancelled = action.cancel("Superseded by a facilities work order.", "investigator-1", NOW);
        assertThat(cancelled.blocksClosure()).isFalse();
    }

    @Test
    void a_verified_capa_cannot_transition_further() {
        SecurityIncident incident = triaged(reported());
        CorrectiveAction verified = CorrectiveAction
                .open(UUID.randomUUID(), incident, "Fix the guardrail.", "owner-1", LocalDate.parse("2026-09-10"),
                        true, "investigator-1", NOW)
                .verify("Done.", "supervisor-1", NOW);
        assertThatThrownBy(() -> verified.cancel("Too late.", "investigator-1", NOW))
                .isInstanceOf(IncidentException.class);
    }

    @Test
    void overdue_is_computed_from_the_due_date_not_stored() {
        SecurityIncident incident = triaged(reported());
        CorrectiveAction action = CorrectiveAction.open(UUID.randomUUID(), incident, "Fix the guardrail.",
                "owner-1", LocalDate.parse("2026-09-01"), true, "investigator-1", NOW);
        assertThat(action.isOverdue(LocalDate.parse("2026-09-02"))).isTrue();
        assertThat(action.isOverdue(LocalDate.parse("2026-08-31"))).isFalse();

        CorrectiveAction verified = action.startProgress("owner-1", NOW).verify("Done.", "supervisor-1", NOW);
        assertThat(verified.isOverdue(LocalDate.parse("2026-12-01"))).isFalse();
    }

    // --- helpers ---

    private SecurityIncident triaged(SecurityIncident incident) {
        return incident.triage(Severity.MEDIUM, new RiskRating(Likelihood.POSSIBLE, Impact.MODERATE), false, null,
                "hse-1", NOW, SourceChannel.WEB, "corr-1");
    }

    private SecurityIncident investigating() {
        return triaged(reported()).openInvestigation("investigator-1", null, "investigator-1", NOW,
                SourceChannel.WEB, "corr-1");
    }

    private SecurityIncident closedIncident() {
        return investigating().close("Closed for the test fixture.", 0, "investigator-1", NOW, SourceChannel.WEB,
                "corr-1");
    }
}
