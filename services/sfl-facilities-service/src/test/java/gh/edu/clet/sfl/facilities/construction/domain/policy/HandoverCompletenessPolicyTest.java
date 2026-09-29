package gh.edu.clet.sfl.facilities.construction.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S176-04's "not silently accepted as done" rule, pure. */
class HandoverCompletenessPolicyTest {

    @Test
    void no_register_change_at_all_is_incomplete() {
        HandoverCompletenessPolicy.Completeness completeness = HandoverCompletenessPolicy.evaluate(0, Set.of(),
                List.of());

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.reason()).contains("lists no S152 register change");
    }

    @Test
    void a_change_is_complete_when_nothing_was_declared() {
        HandoverCompletenessPolicy.Completeness completeness = HandoverCompletenessPolicy.evaluate(1, Set.of(
                UUID.randomUUID()), List.of());

        assertThat(completeness.complete()).isTrue();
    }

    @Test
    void a_declared_space_left_unupdated_is_incomplete_naming_the_space() {
        UUID declared = UUID.randomUUID();

        HandoverCompletenessPolicy.Completeness completeness = HandoverCompletenessPolicy.evaluate(1, Set.of(
                UUID.randomUUID()), List.of(declared));

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.reason()).contains(declared.toString());
    }

    @Test
    void every_declared_space_updated_is_complete() {
        UUID declared = UUID.randomUUID();

        HandoverCompletenessPolicy.Completeness completeness = HandoverCompletenessPolicy.evaluate(1, Set.of(declared),
                List.of(declared));

        assertThat(completeness.complete()).isTrue();
    }
}
