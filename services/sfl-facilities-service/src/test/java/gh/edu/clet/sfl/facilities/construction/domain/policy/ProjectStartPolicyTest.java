package gh.edu.clet.sfl.facilities.construction.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S176-01's permit half of the start gate, pure - {@code S176MandatoryScenariosTest} proves
 * the sign-off half and the end-to-end refusal; this pins the multi-work-type interpretation the S176
 * gap report records (one current permit is required per permit-requiring work type, not just one).
 */
class ProjectStartPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private final UUID projectId = UUID.randomUUID();

    private ProjectPermitLink link(String permitId, String workType) {
        return new ProjectPermitLink(UUID.randomUUID(), projectId, "MAIN", permitId, workType,
                RecordMetadata.createdBy("pm", NOW, SourceChannel.WEB, "corr"));
    }

    private PermitRecord current(String permitId, String workType) {
        return new PermitRecord(UUID.randomUUID(), permitId, null, "MAIN", workType, PermitRecord.Status.ISSUED,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600), null, null, "sfl.ssemp.permit-issued.v1", NOW, null,
                RecordMetadata.createdBy("system", NOW, SourceChannel.INTEGRATION, "corr"));
    }

    @Test
    void no_permit_requiring_work_types_need_nothing() {
        Set<String> missing = ProjectStartPolicy.missingPermits(List.of("FIT_OUT"), Set.of("HOT_WORK"), List.of(),
                Map.of(), NOW);

        assertThat(missing).isEmpty();
    }

    @Test
    void a_permit_requiring_work_type_with_no_link_at_all_is_missing() {
        Set<String> missing = ProjectStartPolicy.missingPermits(List.of("HOT_WORK"), Set.of("HOT_WORK"), List.of(),
                Map.of(), NOW);

        assertThat(missing).containsExactly("HOT_WORK");
    }

    @Test
    void each_permit_requiring_work_type_needs_its_own_current_permit() {
        ProjectPermitLink hotWorkLink = link("PERMIT-1", "HOT_WORK");
        Map<String, PermitRecord> permits = Map.of("PERMIT-1", current("PERMIT-1", "HOT_WORK"));

        // HOT_WORK is covered; CONFINED_SPACE is not, even though a permit exists for the project.
        Set<String> missing = ProjectStartPolicy.missingPermits(List.of("HOT_WORK", "CONFINED_SPACE"),
                Set.of("HOT_WORK", "CONFINED_SPACE"), List.of(hotWorkLink), permits, NOW);

        assertThat(missing).containsExactly("CONFINED_SPACE");
    }

    @Test
    void an_expired_permit_does_not_satisfy_the_gate() {
        ProjectPermitLink hotWorkLink = link("PERMIT-1", "HOT_WORK");
        PermitRecord expired = new PermitRecord(UUID.randomUUID(), "PERMIT-1", null, "MAIN", "HOT_WORK",
                PermitRecord.Status.ISSUED, NOW.minusSeconds(7200), NOW.minusSeconds(3600), null, null,
                "sfl.ssemp.permit-issued.v1", NOW.minusSeconds(3600), null,
                RecordMetadata.createdBy("system", NOW, SourceChannel.INTEGRATION, "corr"));

        Set<String> missing = ProjectStartPolicy.missingPermits(List.of("HOT_WORK"), Set.of("HOT_WORK"),
                List.of(hotWorkLink), Map.of("PERMIT-1", expired), NOW);

        assertThat(missing).containsExactly("HOT_WORK");
    }
}
