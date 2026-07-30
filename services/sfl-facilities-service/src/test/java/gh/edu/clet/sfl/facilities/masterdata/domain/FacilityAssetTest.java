package gh.edu.clet.sfl.facilities.masterdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The facility asset register (SRS-SFL-S152-01, §21.1). */
class FacilityAssetTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 30);
    private static final String ACTOR = "facilities.manager";

    @Test
    void registers_an_asset_as_operational() {
        FacilityAsset asset = generator(AssetCriticality.CRITICAL);

        assertThat(asset.siteCode()).isEqualTo("MAIN");
        assertThat(asset.assetCode()).isEqualTo("GEN-01");
        assertThat(asset.operationalStatus()).isEqualTo(AssetOperationalStatus.OPERATIONAL);
        assertThat(asset.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ACTIVE);
        assertThat(asset.impairsReadiness()).isFalse();
    }

    @Test
    void an_impaired_asset_flags_itself_for_readiness() {
        FacilityAsset failed = generator(AssetCriticality.CRITICAL).changeOperationalStatus(
                AssetOperationalStatus.OUT_OF_SERVICE, "Will not start", ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(failed.impairsReadiness()).isTrue();
        assertThat(failed.operationalStatus().isTotalFailure()).isTrue();
        assertThat(failed.statusNotes()).isEqualTo("Will not start");
        assertThat(failed.statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    void a_decommissioned_asset_raises_nothing() {
        FacilityAsset retired = generator(AssetCriticality.CRITICAL).changeOperationalStatus(
                AssetOperationalStatus.DECOMMISSIONED, "Replaced", ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(retired.impairsReadiness()).isFalse();
    }

    @Test
    void an_archived_asset_cannot_change_status() {
        FacilityAsset archived = generator(AssetCriticality.LOW).changeLifecycle(
                RecordLifecycleStatus.ARCHIVED, ACTOR, NOW, SourceChannel.WEB, null);

        assertThatThrownBy(() -> archived.changeOperationalStatus(AssetOperationalStatus.OUT_OF_SERVICE, null,
                ACTOR, NOW, SourceChannel.WEB, null))
                .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);
    }

    @Test
    void service_is_due_from_installation_when_it_has_never_been_serviced() {
        FacilityAsset asset = generator(AssetCriticality.HIGH);

        assertThat(asset.serviceDueOn()).isEqualTo(LocalDate.of(2026, 4, 1).plusDays(90));
    }

    @Test
    void service_is_due_from_the_last_service_once_there_is_one() {
        FacilityAsset serviced = generator(AssetCriticality.HIGH).recordService(LocalDate.of(2026, 7, 1),
                ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(serviced.serviceDueOn()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(serviced.serviceOverdueOn(TODAY)).isFalse();
    }

    @Test
    void an_asset_past_its_interval_is_overdue() {
        FacilityAsset stale = generator(AssetCriticality.HIGH).recordService(LocalDate.of(2026, 1, 1), ACTOR,
                NOW, SourceChannel.WEB, null);

        assertThat(stale.serviceOverdueOn(TODAY)).isTrue();
    }

    @Test
    void an_asset_with_no_interval_is_never_due() {
        FacilityAsset unscheduled = FacilityAsset.register(UUID.randomUUID(), "MAIN", "DESK-01", "Desk",
                AssetCategory.FURNITURE, AssetCriticality.LOW, null, null, null, null, null, null, null, null,
                null, null, null, ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(unscheduled.serviceDueOn()).isNull();
        assertThat(unscheduled.serviceOverdueOn(TODAY)).isFalse();
    }

    @Test
    void rejects_a_warranty_that_expires_before_installation() {
        assertThatThrownBy(() -> FacilityAsset.register(UUID.randomUUID(), "MAIN", "GEN-02", "Generator",
                AssetCategory.GENERATOR, AssetCriticality.CRITICAL, null, null, null, null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 1, 1), 90, null, null, null, ACTOR, NOW,
                SourceChannel.WEB, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("warrantyExpiresOn cannot precede installedOn");
    }

    @Test
    void rejects_a_non_positive_service_interval() {
        assertThatThrownBy(() -> FacilityAsset.register(UUID.randomUUID(), "MAIN", "GEN-03", "Generator",
                AssetCategory.GENERATOR, AssetCriticality.CRITICAL, null, null, null, null, null, null, null,
                0, null, null, null, ACTOR, NOW, SourceChannel.WEB, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("serviceIntervalDays must be positive");
    }

    @Test
    void defaults_criticality_to_medium_when_unstated() {
        FacilityAsset asset = FacilityAsset.register(UUID.randomUUID(), "MAIN", "PUMP-01", "Pump",
                AssetCategory.PLUMBING, null, null, null, null, null, null, null, null, null, null, null,
                null, ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(asset.criticality()).isEqualTo(AssetCriticality.MEDIUM);
    }

    private static FacilityAsset generator(AssetCriticality criticality) {
        return FacilityAsset.register(UUID.randomUUID(), "main", "gen-01", "Standby generator",
                AssetCategory.GENERATOR, criticality, UUID.randomUUID(), "PLANT-1", "Cummins", "C150", "SN-1",
                LocalDate.of(2026, 4, 1), LocalDate.of(2028, 4, 1), 90, "estates", null, null, ACTOR, NOW,
                SourceChannel.WEB, null);
    }
}
