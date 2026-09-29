package gh.edu.clet.sfl.facilities.construction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** The explicit S176-01/-04 transition table, pinned so a future change to it is a deliberate edit. */
class ProjectStatusTest {

    @Test
    void the_transition_table_is_exactly_this() {
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.PROPOSED))
                .isEqualTo(EnumSet.of(ProjectStatus.REGISTERED, ProjectStatus.CANCELLED));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.REGISTERED))
                .isEqualTo(EnumSet.of(ProjectStatus.APPROVED, ProjectStatus.CANCELLED));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.APPROVED))
                .isEqualTo(EnumSet.of(ProjectStatus.IN_PROGRESS, ProjectStatus.REGISTERED, ProjectStatus.CANCELLED));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.IN_PROGRESS))
                .isEqualTo(EnumSet.of(ProjectStatus.PRACTICAL_COMPLETION, ProjectStatus.CANCELLED));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.PRACTICAL_COMPLETION))
                .isEqualTo(EnumSet.of(ProjectStatus.HANDED_OVER));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.HANDED_OVER))
                .isEqualTo(EnumSet.of(ProjectStatus.CLOSED));
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.CLOSED)).isEmpty();
        assertThat(ProjectStatus.allowedFrom(ProjectStatus.CANCELLED)).isEmpty();
    }

    @Test
    void closed_and_cancelled_are_the_only_terminal_states() {
        for (ProjectStatus status : ProjectStatus.values()) {
            boolean terminal = status == ProjectStatus.CLOSED || status == ProjectStatus.CANCELLED;
            assertThat(status.isTerminal()).as(status.name()).isEqualTo(terminal);
        }
    }

    @Test
    void only_pre_start_states_may_still_revise_the_baseline() {
        assertThat(ProjectStatus.PROPOSED.isPreStart()).isTrue();
        assertThat(ProjectStatus.REGISTERED.isPreStart()).isTrue();
        assertThat(ProjectStatus.APPROVED.isPreStart()).isTrue();
        assertThat(ProjectStatus.IN_PROGRESS.isPreStart()).isFalse();
        assertThat(ProjectStatus.HANDED_OVER.isPreStart()).isFalse();
    }

    @Test
    void variations_are_accepted_from_approval_to_practical_completion_only() {
        assertThat(ProjectStatus.REGISTERED.acceptsVariations()).isFalse();
        assertThat(ProjectStatus.APPROVED.acceptsVariations()).isTrue();
        assertThat(ProjectStatus.IN_PROGRESS.acceptsVariations()).isTrue();
        assertThat(ProjectStatus.PRACTICAL_COMPLETION.acceptsVariations()).isTrue();
        assertThat(ProjectStatus.HANDED_OVER.acceptsVariations()).isFalse();
    }
}
