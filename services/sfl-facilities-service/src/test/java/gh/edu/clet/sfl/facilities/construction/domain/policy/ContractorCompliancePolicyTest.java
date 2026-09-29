package gh.edu.clet.sfl.facilities.construction.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S176-02's lapse rule, pure and shared by the request path and the sweep. */
class ContractorCompliancePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private Contractor contractor(LocalDate insuranceExpiry) {
        return Contractor.register(UUID.randomUUID(), "MAIN", "ACME-01", "Acme Builders", null, null, null,
                insuranceExpiry, "seed", NOW, SourceChannel.WEB, "corr");
    }

    @Test
    void compliant_when_everything_is_still_in_force() {
        ContractorCompliancePolicy.Compliance compliance = ContractorCompliancePolicy.evaluate(
                contractor(LocalDate.of(2030, 1, 1)), List.of(), TODAY, 30);

        assertThat(compliance.compliant()).isTrue();
        assertThat(compliance.lapses()).isEmpty();
    }

    @Test
    void expired_insurance_is_a_lapse_naming_the_expiry_date() {
        ContractorCompliancePolicy.Compliance compliance = ContractorCompliancePolicy.evaluate(
                contractor(TODAY.minusDays(1)), List.of(), TODAY, 30);

        assertThat(compliance.compliant()).isFalse();
        assertThat(compliance.lapses()).hasSize(1);
        assertThat(compliance.lapses().get(0)).contains("Insurance").contains(TODAY.minusDays(1).toString());
    }

    @Test
    void an_expiry_on_today_is_still_in_force_and_lapses_the_day_after() {
        ContractorCompliancePolicy.Compliance compliance = ContractorCompliancePolicy.evaluate(contractor(TODAY),
                List.of(), TODAY, 30);

        assertThat(compliance.compliant()).isTrue();
    }

    @Test
    void every_lapsed_competency_is_named_not_only_the_first() {
        Contractor contractor = contractor(LocalDate.of(2030, 1, 1));
        ContractorCompetency lapsedOne = ContractorCompetency.record(UUID.randomUUID(), contractor, "HOT_WORK", null,
                null, TODAY.minusDays(5), "seed", NOW, SourceChannel.WEB, "corr");
        ContractorCompetency lapsedTwo = ContractorCompetency.record(UUID.randomUUID(), contractor, "CONFINED_SPACE",
                null, null, TODAY.minusDays(1), "seed", NOW, SourceChannel.WEB, "corr");

        ContractorCompliancePolicy.Compliance compliance = ContractorCompliancePolicy.evaluate(contractor,
                List.of(lapsedOne, lapsedTwo), TODAY, 30);

        assertThat(compliance.compliant()).isFalse();
        assertThat(compliance.lapses()).hasSize(2);
        assertThat(compliance.lapses().toString()).contains("HOT_WORK").contains("CONFINED_SPACE");
    }

    @Test
    void something_expiring_within_the_warning_window_is_flagged_but_still_compliant() {
        ContractorCompliancePolicy.Compliance compliance = ContractorCompliancePolicy.evaluate(
                contractor(TODAY.plusDays(5)), List.of(), TODAY, 30);

        assertThat(compliance.compliant()).isTrue();
        assertThat(compliance.expiringSoon()).hasSize(1);
    }
}
