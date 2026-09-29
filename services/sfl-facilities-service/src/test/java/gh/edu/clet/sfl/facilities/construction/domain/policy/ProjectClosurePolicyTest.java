package gh.edu.clet.sfl.facilities.construction.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectOrigin;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S176-04 "the project cannot close until all defects-liability items are closed or deferred". */
class ProjectClosurePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

    private ConstructionProject handedOver(LocalDate liabilityEndsOn) {
        return new ConstructionProject(UUID.randomUUID(), "CP-MAIN-00001", "MAIN", "Title", "Scope", List.of(),
                new java.math.BigDecimal("500000.00"), "GHS", 1, "FUND-1", null, "pm", ProjectStatus.HANDED_OVER,
                ProjectOrigin.DIRECT, null, null, List.of(), null, null, UUID.randomUUID(), NOW, NOW, NOW,
                liabilityEndsOn, null, null, "pm", NOW, RecordMetadata.createdBy("pm", NOW, SourceChannel.WEB, "corr"));
    }

    private DefectItem defect(DefectItem.Status status) {
        DefectItem raised = DefectItem.raise(UUID.randomUUID(), "DL-MAIN-00001", handedOver(LocalDate.of(2027, 9, 28)),
                UUID.randomUUID(), "Leak", null, null, DefectItem.Priority.LOW, "pm", NOW, SourceChannel.WEB, "corr");
        return switch (status) {
            case OPEN -> raised;
            case CLOSED -> raised.closeFromWorkOrder("CLOSED", "pm", NOW, SourceChannel.WEB, "corr");
            case DEFERRED -> raised.defer("Accepted as-is", "pm", NOW, SourceChannel.WEB, "corr");
        };
    }

    @Test
    void an_open_defect_refuses_closure_naming_it() {
        ConstructionProject project = handedOver(LocalDate.of(2020, 1, 1));
        DefectItem open = defect(DefectItem.Status.OPEN);

        ProjectClosurePolicy.Decision decision = ProjectClosurePolicy.evaluate(project, List.of(open),
                LocalDate.of(2026, 9, 28));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.refusal()).isEqualTo(FacilitiesErrorCode.PROJECT_DEFECTS_OPEN);
        assertThat(decision.reason()).contains(open.defectReference());
    }

    @Test
    void closed_and_deferred_defects_do_not_block_closure() {
        ConstructionProject project = handedOver(LocalDate.of(2020, 1, 1));

        ProjectClosurePolicy.Decision decision = ProjectClosurePolicy.evaluate(project,
                List.of(defect(DefectItem.Status.CLOSED), defect(DefectItem.Status.DEFERRED)),
                LocalDate.of(2026, 9, 28));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void closure_before_the_liability_period_ends_is_refused() {
        ConstructionProject project = handedOver(LocalDate.of(2027, 1, 1));

        ProjectClosurePolicy.Decision decision = ProjectClosurePolicy.evaluate(project, List.of(),
                LocalDate.of(2026, 9, 28));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("2027-01-01");
    }

    @Test
    void only_a_handed_over_project_may_close() {
        ConstructionProject project = handedOver(LocalDate.of(2020, 1, 1)).close("pm", NOW, SourceChannel.WEB,
                "corr");

        ProjectClosurePolicy.Decision decision = ProjectClosurePolicy.evaluate(project, List.of(),
                LocalDate.of(2026, 9, 28));

        assertThat(decision.allowed()).isFalse();
    }
}
