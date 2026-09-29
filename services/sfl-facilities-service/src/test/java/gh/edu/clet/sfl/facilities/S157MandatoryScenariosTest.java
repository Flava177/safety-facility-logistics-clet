package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyVarianceService;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.support.EnergyTestHarness;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S157 acceptance criteria, end to end through the application services - one nested class per
 * SRS-SFL-S157-0N requirement, one test per acceptance criterion, validation rule and error state.
 */
class S157MandatoryScenariosTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private EnergyTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new EnergyTestHarness();
    }

    // =============================================================================================
    // SRS-SFL-S157-01 - Utility Metering Ingestion and Consumption Aggregation
    // =============================================================================================

    @Nested
    class MeterRegistrationAndResolution {

        @Test
        @DisplayName("a meter registers with canonical unit derived from its utility, resolved to site/building")
        void registers_resolved_to_site_and_building() {
            EnergyMeter meter = registerElectricity("ELEC-01", MeterSource.MANUAL, null, null);

            assertThat(meter.siteCode()).isEqualTo("MAIN");
            assertThat(meter.buildingCode()).isEqualTo("LAW");
            assertThat(meter.utility()).isEqualTo(Utility.ELECTRICITY);
            assertThat(meter.utility().unitCode()).isEqualTo("kWh");
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-meter-registered.v1")).isTrue();
            assertThat(harness.base.audit.recorded(AuditAction.ENERGY_METER_REGISTERED)).isTrue();
        }

        @Test
        @DisplayName("a meter can be resolved to a room within the building")
        void resolves_to_a_room() {
            EnergyMeter meter = harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW",
                    harness.base.meetingRoom.id(), "ELEC-ROOM", "Meeting Room Sub-meter", Utility.ELECTRICITY,
                    MeterSource.MANUAL, null, null, null, harness.energyOfficer(), SourceChannel.WEB, null, null));

            assertThat(meter.roomId()).isEqualTo(harness.base.meetingRoom.id());
        }

        @Test
        @DisplayName("a water meter is registered in m3, a generator-fuel meter in litres")
        void other_utilities_take_their_canonical_unit() {
            EnergyMeter water = registerMeter("WATER-01", Utility.WATER, MeterSource.MANUAL, null, null);
            EnergyMeter fuel = registerMeter("FUEL-01", Utility.GENERATOR_FUEL, MeterSource.MANUAL, null, null);

            assertThat(water.utility().unitCode()).isEqualTo("m3");
            assertThat(fuel.utility().unitCode()).isEqualTo("litres");
        }

        @Test
        @DisplayName("a building that does not exist at the site is refused")
        void unknown_building_is_refused() {
            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "NOPE", null,
                    "X-01", "X", Utility.ELECTRICITY, MeterSource.MANUAL, null, null, null, harness.energyOfficer(),
                    SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.InvalidParentReferenceException.class);
        }

        @Test
        @DisplayName("an AMI meter needs the vendor's meter reference")
        void ami_meter_needs_vendor_reference() {
            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "AMI-NOREF", "AMI meter", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-100", null, null,
                    harness.energyOfficer(), SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("vendor's meter reference");
        }

        @Test
        @DisplayName("registering a meter needs FACILITIES_ENERGY_METER_MANAGE - a requester cannot")
        void refused_without_permission() {
            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "X-02", "X", Utility.ELECTRICITY, MeterSource.MANUAL, null, null, null, harness.base.requester,
                    SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("registering at a site outside the actor's scope is refused")
        void refused_for_another_site() {
            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("KSI", "KSB", null,
                    "X-03", "X", Utility.ELECTRICITY, MeterSource.MANUAL, null, null, null, harness.energyOfficer(),
                    SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    @Nested
    class SharedTelemetryWithBmsIot {

        @Test
        @DisplayName("S157-04: an AVAMP id already an S156 device must register as BMS_STREAM, not AMI")
        void ami_registration_over_an_s156_device_is_refused() {
            harness.devices.enrol("AVAMP-500", "SENSOR-500", "MAIN");

            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "AMI-CONFLICT", "AMI meter", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-500", "VENDOR-REF-1",
                    null, harness.energyOfficer(), SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED));
        }

        @Test
        @DisplayName("S157-04: the same conflict refuses a MANUAL registration too")
        void manual_registration_over_an_s156_device_is_also_refused() {
            harness.devices.enrol("AVAMP-501", "SENSOR-501", "MAIN");

            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "MAN-CONFLICT", "Manual meter", Utility.ELECTRICITY, MeterSource.MANUAL, "AVAMP-501", null, null,
                    harness.energyOfficer(), SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED));
        }

        @Test
        @DisplayName("S157-04: BMS_STREAM registration over an S156 device succeeds and carries its device id")
        void bms_stream_registration_succeeds() {
            harness.devices.enrol("AVAMP-502", "SENSOR-502", "MAIN");

            EnergyMeter meter = harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "BMS-OK", "Stream meter", Utility.ELECTRICITY, MeterSource.BMS_STREAM, "AVAMP-502", null, null,
                    harness.energyOfficer(), SourceChannel.WEB, null, null));

            assertThat(meter.bmsDeviceId()).isNotNull();
            assertThat(meter.source()).isEqualTo(MeterSource.BMS_STREAM);
        }

        @Test
        @DisplayName("S157-04: one AVAMP identity, one meter - a second registration for the same id is refused")
        void double_registration_of_the_same_identity_is_refused() {
            registerMeter("FIRST", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-503", "VENDOR-FIRST");

            assertThatThrownBy(() -> harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null,
                    "SECOND", "Second", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-503", "VENDOR-SECOND", null,
                    harness.energyOfficer(), SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED));
        }

        @Test
        @DisplayName("S157-04: S157 consumes S156's stream rather than opening a second vendor connection")
        void stream_reading_is_consumed_without_a_second_vendor_connection() {
            harness.devices.enrol("AVAMP-504", "SENSOR-504", "MAIN");
            EnergyMeter meter = registerMeter("STREAM-01", Utility.ELECTRICITY, MeterSource.BMS_STREAM, "AVAMP-504",
                    null);

            EnergyReadingService.StreamOutcome outcome = harness.readings.consumeStream(
                    new EnergyCommands.StreamReading(UUID.randomUUID(), meter.bmsDeviceId(), "AVAMP-504", "MAIN",
                            Utility.ELECTRICITY, new BigDecimal("12.5"), NOW));

            assertThat(outcome).isEqualTo(EnergyReadingService.StreamOutcome.POSTED);
            List<ConsumptionReading> posted = harness.repository.findReadings(
                    new EnergyRepository.ReadingQuery("MAIN", meter.id(), ReadingStatus.POSTED, null, null, 10));
            assertThat(posted).hasSize(1);
            assertThat(posted.get(0).consumption()).isEqualByComparingTo("12.5");
        }

        @Test
        @DisplayName("S157-04: a stream reading for a meter registered under another source is refused, not posted")
        void stream_reading_for_a_conflicting_meter_is_refused() {
            EnergyMeter meter = registerMeter("AMI-STILL", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-505",
                    "VENDOR-505");

            EnergyReadingService.StreamOutcome outcome = harness.readings.consumeStream(
                    new EnergyCommands.StreamReading(UUID.randomUUID(), UUID.randomUUID(), "AVAMP-505", "MAIN",
                            Utility.ELECTRICITY, new BigDecimal("5"), NOW));

            assertThat(outcome).isEqualTo(EnergyReadingService.StreamOutcome.REFUSED);
            assertThat(harness.repository.findMeter(meter.id())).isPresent();
            assertThat(harness.repository.findReadings(
                    new EnergyRepository.ReadingQuery("MAIN", meter.id(), null, null, null, 10))).isEmpty();
        }
    }

    @Nested
    class AmiIngestion {

        @Test
        @DisplayName("a correctly signed AMI reading converts vendor units and posts to the consumption record")
        void ami_reading_posts_and_converts_units() {
            EnergyMeter meter = registerMeter("AMI-01", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-600",
                    "VENDOR-600");

            EnergyReadingService.IngestResult result = harness.readings.ingest(
                    signed("k1", Map.of("meterRef", "VENDOR-600", "value", "1500", "unit", "Wh",
                            "intervalStart", "2026-09-01T07:00:00Z", "intervalEnd", "2026-09-01T08:00:00Z")),
                    harness.integration());

            assertThat(result.duplicate()).isFalse();
            assertThat(result.reading().consumption()).isEqualByComparingTo("1.5000");
            assertThat(result.reading().status()).isEqualTo(ReadingStatus.POSTED);
            List<gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption> daily = harness.repository.findDaily(
                    "MAIN", Utility.ELECTRICITY, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));
            assertThat(daily).hasSize(1);
            assertThat(daily.get(0).consumption()).isEqualByComparingTo("1.5000");
        }

        @Test
        @DisplayName("a repeated idempotency key answers as before, without posting twice")
        void duplicate_message_is_not_reposted() {
            registerMeter("AMI-02", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-601", "VENDOR-601");
            SignedVendorMessage message = signed("k2", Map.of("meterRef", "VENDOR-601", "value", "1000", "unit", "Wh",
                    "intervalEnd", "2026-09-01T08:00:00Z"));

            harness.readings.ingest(message, harness.integration());
            EnergyReadingService.IngestResult replay = harness.readings.ingest(message, harness.integration());

            assertThat(replay.duplicate()).isTrue();
            assertThat(harness.repository.findReadings(
                    new EnergyRepository.ReadingQuery("MAIN", null, ReadingStatus.POSTED, null, null, 10)))
                    .hasSize(1);
        }

        @Test
        @DisplayName("NFR-SEC2: a forged AMI message (bad signature) is rejected and logged, and nothing is posted")
        void forged_message_is_rejected_and_nothing_is_posted() {
            registerMeter("AMI-03", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-602", "VENDOR-602");
            Map<String, Object> payload = Map.of("meterRef", "VENDOR-602", "value", "1000", "unit", "Wh",
                    "intervalEnd", "2026-08-01T08:00:00Z");
            SignedVendorMessage forged = new SignedVendorMessage(EnergyTestHarness.AMI_SOURCE, "energy.reading",
                    "k3", "MAIN", harness.base.clock.instant(),
                    "0000000000000000000000000000000000000000000000000000000000000000", body(payload), payload);

            assertThatThrownBy(() -> harness.readings.ingest(forged, harness.integration()))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));

            assertThat(harness.repository.findReadings(
                    new EnergyRepository.ReadingQuery("MAIN", null, null, null, null, 10))).isEmpty();
            assertThat(harness.base.audit.recorded(AuditAction.VENDOR_MESSAGE_REJECTED)).isTrue();
        }

        @Test
        @DisplayName("an unknown meter reference is rejected through the verifier, not silently ignored")
        void unknown_meter_reference_is_rejected() {
            assertThatThrownBy(() -> harness.readings.ingest(
                    signed("k4", Map.of("meterRef", "NO-SUCH-METER", "value", "1", "unit", "kWh", "intervalEnd",
                            "2026-09-01T08:00:00Z")),
                    harness.integration()))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));
        }

        @Test
        @DisplayName("an unrecognised unit is rejected rather than guessed at")
        void unrecognised_unit_is_rejected() {
            registerMeter("AMI-04", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-603", "VENDOR-603");

            assertThatThrownBy(() -> harness.readings.ingest(
                    signed("k5", Map.of("meterRef", "VENDOR-603", "value", "5", "unit", "gallons", "intervalEnd",
                            "2026-09-01T08:00:00Z")),
                    harness.integration()))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));
        }

        @Test
        @DisplayName("ingestion requires FACILITIES_ENERGY_READING_INGEST - a facilities manager cannot")
        void ingestion_refused_without_permission() {
            registerMeter("AMI-05", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-604", "VENDOR-604");

            assertThatThrownBy(() -> harness.readings.ingest(
                    signed("k6", Map.of("meterRef", "VENDOR-604", "value", "1", "unit", "kWh", "intervalEnd",
                            "2026-09-01T08:00:00Z")),
                    harness.base.manager))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    @Nested
    class ManualEntryAndVerification {

        @Test
        @DisplayName("a plausible manual reading posts directly to the consumption record")
        void plausible_reading_posts() {
            EnergyMeter meter = registerMeter("MAN-01", Utility.WATER, MeterSource.MANUAL, null, null);
            enterManual(meter.id(), "100", NOW);

            ConsumptionReading second = enterManual(meter.id(), "150", NOW.plus(Duration.ofDays(1)));

            assertThat(second.status()).isEqualTo(ReadingStatus.POSTED);
            assertThat(second.consumption()).isEqualByComparingTo("50");
            assertThat(harness.base.audit.recorded(AuditAction.ENERGY_READING_ENTERED)).isTrue();
        }

        @Test
        @DisplayName("S157-01: a reading outside the plausibility band is held, not posted directly")
        void implausible_reading_is_held() {
            EnergyMeter meter = registerMeter("MAN-02", Utility.WATER, MeterSource.MANUAL, null, null);
            enterManual(meter.id(), "100", NOW);
            enterManual(meter.id(), "150", NOW.plus(Duration.ofDays(1)));
            enterManual(meter.id(), "200", NOW.plus(Duration.ofDays(2)));

            // Trailing daily average around 50/day; a delta of 500 in a day is wildly outside the band.
            ConsumptionReading held = enterManual(meter.id(), "700", NOW.plus(Duration.ofDays(3)));

            assertThat(held.status()).isEqualTo(ReadingStatus.HELD);
            assertThat(held.holdReason()).contains("Implausible Reading");
            assertThat(harness.base.audit.recorded(AuditAction.ENERGY_READING_HELD)).isTrue();
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-reading-held.v1")).isTrue();
            // Not posted: the consumption record does not reflect the held delta.
            List<gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption> daily = harness.repository.findDailyForMeter(
                    meter.id(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
            BigDecimal total = daily.stream().map(gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption::consumption)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("too little history: a reading posts unchecked rather than being held by default")
        void too_little_history_posts_unchecked() {
            EnergyMeter meter = registerMeter("MAN-03", Utility.WATER, MeterSource.MANUAL, null, null);
            ConsumptionReading baseline = enterManual(meter.id(), "100", NOW);
            assertThat(baseline.consumption()).isNull();

            ConsumptionReading second = enterManual(meter.id(), "9000", NOW.plus(Duration.ofDays(1)));
            assertThat(second.status()).isEqualTo(ReadingStatus.POSTED);
            assertThat(second.plausibilityChecked()).isFalse();
        }

        @Test
        @DisplayName("a held reading is verified by a supervisor and then posts to the consumption record")
        void verification_posts_the_reading() {
            EnergyMeter meter = registerMeter("MAN-04", Utility.WATER, MeterSource.MANUAL, null, null);
            enterManual(meter.id(), "100", NOW);
            enterManual(meter.id(), "150", NOW.plus(Duration.ofDays(1)));
            enterManual(meter.id(), "200", NOW.plus(Duration.ofDays(2)));
            ConsumptionReading held = enterManual(meter.id(), "900", NOW.plus(Duration.ofDays(3)));

            ConsumptionReading verified = harness.readings.decide(new EnergyCommands.DecideHeldReading(held.id(),
                    true, null, "Confirmed with site walk", harness.director(), SourceChannel.WEB));

            assertThat(verified.status()).isEqualTo(ReadingStatus.POSTED);
            assertThat(verified.verifiedBy()).isEqualTo(harness.director().actorId());
            assertThat(harness.base.audit.recorded(AuditAction.ENERGY_READING_VERIFIED)).isTrue();
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-reading-verified.v1")).isTrue();
        }

        @Test
        @DisplayName("S157-01: a held reading must be verified by someone other than the person who entered it")
        void self_verification_is_refused() {
            // SFL_ADMIN holds every FACILITIES_* permission, including both ENTER and VERIFY - the one actor
            // who can reach this rule at all, since the permission matrix otherwise separates the two by role
            // (the energy officer enters and cannot verify; the director verifies and cannot enter). The rule
            // exists precisely so that separation is not the only thing enforcing it.
            EnergyMeter meter = registerMeter("MAN-05", Utility.WATER, MeterSource.MANUAL, null, null);
            ConsumptionReading held = enterManualAs(harness.base.system, meter.id(), "100", NOW);
            ConsumptionReading forcedHeld = new ConsumptionReading(held.id(), held.siteCode(), held.meterId(),
                    held.buildingCode(), held.utility(), held.source(), ReadingStatus.HELD, held.observedAt(),
                    held.intervalStart(), held.registerValue(), held.consumption(), held.vendorValue(),
                    held.vendorUnit(), held.sourceReference(), held.plausibilityChecked(),
                    held.trailingDailyAverage(), held.bandLow(), held.bandHigh(), "test hold", held.enteredBy(),
                    held.enteredAt(), null, null, null, held.note(), held.metadata());
            harness.repository.saveReading(forcedHeld);

            assertThatThrownBy(() -> harness.readings.decide(new EnergyCommands.DecideHeldReading(held.id(), true,
                    BigDecimal.TEN, null, harness.base.system, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.ENERGY_SELF_VERIFICATION));
        }

        @Test
        @DisplayName("rejecting a held reading requires a reason and never posts consumption")
        void rejection_requires_a_reason_and_posts_nothing() {
            EnergyMeter meter = registerMeter("MAN-06", Utility.WATER, MeterSource.MANUAL, null, null);
            enterManual(meter.id(), "100", NOW);
            enterManual(meter.id(), "150", NOW.plus(Duration.ofDays(1)));
            enterManual(meter.id(), "200", NOW.plus(Duration.ofDays(2)));
            ConsumptionReading held = enterManual(meter.id(), "900", NOW.plus(Duration.ofDays(3)));

            assertThatThrownBy(() -> harness.readings.decide(new EnergyCommands.DecideHeldReading(held.id(), false,
                    null, null, harness.director(), SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);

            ConsumptionReading rejected = harness.readings.decide(new EnergyCommands.DecideHeldReading(held.id(),
                    false, null, "Confirmed a data-entry error with the site", harness.director(), SourceChannel.WEB));
            assertThat(rejected.status()).isEqualTo(ReadingStatus.REJECTED);
        }

        @Test
        @DisplayName("verifying a held reading requires FACILITIES_ENERGY_READING_VERIFY")
        void verification_refused_without_permission() {
            EnergyMeter meter = registerMeter("MAN-07", Utility.WATER, MeterSource.MANUAL, null, null);
            enterManual(meter.id(), "100", NOW);
            enterManual(meter.id(), "150", NOW.plus(Duration.ofDays(1)));
            enterManual(meter.id(), "200", NOW.plus(Duration.ofDays(2)));
            ConsumptionReading held = enterManual(meter.id(), "900", NOW.plus(Duration.ofDays(3)));

            assertThatThrownBy(() -> harness.readings.decide(new EnergyCommands.DecideHeldReading(held.id(), true,
                    null, null, harness.energyOfficer(), SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    @Nested
    class Aggregation {

        @Test
        @DisplayName("readings aggregate to daily and monthly consumption per utility and site")
        void aggregates_daily_and_monthly() {
            EnergyMeter meter = registerMeter("AGG-01", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-700",
                    "VENDOR-700");
            harness.readings.ingest(signed("a1", Map.of("meterRef", "VENDOR-700", "value", "1", "unit", "kWh",
                    "intervalStart", "2026-09-01T00:00:00Z", "intervalEnd", "2026-09-01T01:00:00Z")),
                    harness.integration());
            harness.readings.ingest(signed("a2", Map.of("meterRef", "VENDOR-700", "value", "2", "unit", "kWh",
                    "intervalStart", "2026-09-01T01:00:00Z", "intervalEnd", "2026-09-01T02:00:00Z")),
                    harness.integration());
            harness.readings.ingest(signed("a3", Map.of("meterRef", "VENDOR-700", "value", "3", "unit", "kWh",
                    "intervalStart", "2026-09-02T00:00:00Z", "intervalEnd", "2026-09-02T01:00:00Z")),
                    harness.integration());

            List<EnergyReadingService.ConsumptionPoint> daily = harness.readings.consumption("MAIN",
                    Utility.ELECTRICITY, EnergyPeriod.PeriodType.DAY, EnergyReadingService.Grouping.SITE,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), harness.energyOfficer(), SourceChannel.WEB);
            assertThat(daily).hasSize(2);
            assertThat(daily.stream().filter(p -> p.period().start().equals(LocalDate.of(2026, 9, 1))).findFirst()
                    .orElseThrow().consumption()).isEqualByComparingTo("3");

            List<EnergyReadingService.ConsumptionPoint> monthly = harness.readings.consumption("MAIN",
                    Utility.ELECTRICITY, EnergyPeriod.PeriodType.MONTH, EnergyReadingService.Grouping.SITE,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), harness.energyOfficer(), SourceChannel.WEB);
            assertThat(monthly).hasSize(1);
            assertThat(monthly.get(0).consumption()).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("consumption can be rolled up per building as well as per site")
        void aggregates_per_building() {
            EnergyMeter meter = registerMeter("AGG-02", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-701",
                    "VENDOR-701");
            harness.readings.ingest(signed("b1", Map.of("meterRef", "VENDOR-701", "value", "4", "unit", "kWh",
                    "intervalEnd", "2026-09-01T01:00:00Z")), harness.integration());

            List<EnergyReadingService.ConsumptionPoint> byBuilding = harness.readings.consumption("MAIN",
                    Utility.ELECTRICITY, EnergyPeriod.PeriodType.DAY, EnergyReadingService.Grouping.BUILDING,
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), harness.energyOfficer(), SourceChannel.WEB);
            assertThat(byBuilding).hasSize(1);
            assertThat(byBuilding.get(0).buildingCode()).isEqualTo(meter.buildingCode());
        }
    }

    // =============================================================================================
    // SRS-SFL-S157-02 - Utility Budgets, Tariffs and Variance Alerting
    // =============================================================================================

    @Nested
    class BudgetsTariffsAndVariance {

        @Test
        @DisplayName("consumption exceeding the threshold raises a variance alert naming site and utility at close")
        void variance_alert_names_site_and_utility() {
            EnergyMeter meter = registerMeter("VAR-01", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-800",
                    "VENDOR-800");
            harness.variance.createBudget(new EnergyCommands.CreateBudget("MAIN", Utility.ELECTRICITY,
                    LocalDate.of(2026, 8, 1), new BigDecimal("100"), null, null, null, harness.director(),
                    SourceChannel.WEB));
            harness.readings.ingest(signed("v1", Map.of("meterRef", "VENDOR-800", "value", "1000", "unit", "kWh",
                    "intervalStart", "2026-08-01T00:00:00Z", "intervalEnd", "2026-08-01T01:00:00Z")),
                    harness.integration());

            List<PeriodVarianceResult> closed = harness.variance.closePeriod(new EnergyCommands.ClosePeriod("MAIN",
                    Utility.ELECTRICITY, LocalDate.of(2026, 8, 1), harness.director(), SourceChannel.WEB));

            assertThat(closed).hasSize(1);
            PeriodVarianceResult result = closed.get(0);
            assertThat(result.varianceAlert()).isTrue();
            List<EnergyAlert> alerts = harness.variance.alerts("MAIN", EnergyAlert.EnergyAlertType.VARIANCE, null,
                    100, harness.director(), SourceChannel.WEB);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).message()).contains("MAIN").contains("ELECTRICITY");
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-variance-alert-raised.v1")).isTrue();
        }

        @Test
        @DisplayName("versioned: a budget changed after close leaves the closed period's variance unchanged")
        void closed_variance_is_immutable_to_later_budget_changes() {
            EnergyMeter meter = registerMeter("VAR-02", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-801",
                    "VENDOR-801");
            harness.variance.createBudget(new EnergyCommands.CreateBudget("MAIN", Utility.ELECTRICITY,
                    LocalDate.of(2026, 8, 1), new BigDecimal("100"), null, null, null, harness.director(),
                    SourceChannel.WEB));
            harness.readings.ingest(signed("v2", Map.of("meterRef", "VENDOR-801", "value", "1000", "unit", "kWh",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());

            List<PeriodVarianceResult> firstClose = harness.variance.closePeriod(new EnergyCommands.ClosePeriod(
                    "MAIN", Utility.ELECTRICITY, LocalDate.of(2026, 8, 1), harness.director(), SourceChannel.WEB));
            BigDecimal originalVariance = firstClose.get(0).variancePct();
            int originalBudgetVersion = firstClose.get(0).budgetVersion();

            // A generous new budget version, created after close.
            harness.variance.createBudget(new EnergyCommands.CreateBudget("MAIN", Utility.ELECTRICITY,
                    LocalDate.of(2026, 8, 1), new BigDecimal("10000"), null, null, "revised upward", harness.director(),
                    SourceChannel.WEB));

            List<PeriodVarianceResult> stillClosed = harness.variance.closePeriod(new EnergyCommands.ClosePeriod(
                    "MAIN", Utility.ELECTRICITY, LocalDate.of(2026, 8, 1), harness.director(), SourceChannel.WEB));

            assertThat(stillClosed.get(0).variancePct()).isEqualByComparingTo(originalVariance);
            assertThat(stillClosed.get(0).budgetVersion()).isEqualTo(originalBudgetVersion);
        }

        @Test
        @DisplayName("a spike beyond the trailing baseline raises an anomaly flag even while within budget")
        void anomaly_raised_within_budget() {
            EnergyMeter meter = registerMeter("VAR-03", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-802",
                    "VENDOR-802");
            // A generous budget so the site is nowhere near a variance alert.
            harness.variance.createBudget(new EnergyCommands.CreateBudget("MAIN", Utility.ELECTRICITY,
                    LocalDate.of(2026, 9, 1), new BigDecimal("1000000"), null, null, null, harness.director(),
                    SourceChannel.WEB));
            for (int day = 1; day <= 10; day++) {
                ingestDailyKwh(meter, LocalDate.of(2026, 9, day), "10");
            }
            ingestDailyKwh(meter, LocalDate.of(2026, 9, 11), "1000");

            List<EnergyAlert> raised = harness.variance.evaluateAnomalies(LocalDate.of(2026, 9, 11), "MAIN",
                    harness.director(), SourceChannel.WEB);

            assertThat(raised).hasSize(1);
            assertThat(raised.get(0).type()).isEqualTo(EnergyAlert.EnergyAlertType.ANOMALY);
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-anomaly-flagged.v1")).isTrue();
        }

        @Test
        @DisplayName("S157-02: consumption with no configured tariff is flagged, cost is never assumed zero")
        void missing_tariff_is_flagged_never_zero_cost() {
            EnergyMeter meter = registerMeter("VAR-04", Utility.WATER, MeterSource.AMI, "AVAMP-803", "VENDOR-803");
            harness.readings.ingest(signed("v4", Map.of("meterRef", "VENDOR-803", "value", "5", "unit", "m3",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());

            assertThatThrownBy(() -> harness.variance.cost("MAIN", Utility.WATER, LocalDate.of(2026, 8, 1),
                    harness.director(), SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.class)
                    .satisfies(ex -> assertThat(((FacilitiesException) ex).code())
                            .isEqualTo(FacilitiesErrorCode.ENERGY_TARIFF_MISSING));

            List<PeriodVarianceResult> closed = harness.variance.closePeriod(new EnergyCommands.ClosePeriod("MAIN",
                    Utility.WATER, LocalDate.of(2026, 8, 1), harness.director(), SourceChannel.WEB));
            assertThat(closed.get(0).tariffMissing()).isTrue();
            assertThat(closed.get(0).cost()).isNull();
            assertThat(harness.base.outbox.published("sfl.ifimp.energy-tariff-missing.v1")).isTrue();
        }

        @Test
        @DisplayName("a configured tariff prices consumption without a missing-tariff flag")
        void configured_tariff_prices_consumption() {
            EnergyMeter meter = registerMeter("VAR-05", Utility.WATER, MeterSource.AMI, "AVAMP-804", "VENDOR-804");
            harness.variance.createTariff(new EnergyCommands.CreateTariff("MAIN", Utility.WATER, new BigDecimal("2.50"),
                    "GHS", LocalDate.of(2026, 1, 1), null, null, harness.director(), SourceChannel.WEB));
            harness.readings.ingest(signed("v5", Map.of("meterRef", "VENDOR-804", "value", "10", "unit", "m3",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());

            EnergyVarianceService.CostEstimate cost = harness.variance.cost("MAIN", Utility.WATER,
                    LocalDate.of(2026, 8, 1), harness.director(), SourceChannel.WEB);

            assertThat(cost.cost()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("a tariff's unit rate must be more than zero")
        void zero_tariff_rate_is_refused() {
            assertThatThrownBy(() -> harness.variance.createTariff(new EnergyCommands.CreateTariff("MAIN",
                    Utility.WATER, BigDecimal.ZERO, "GHS", LocalDate.of(2026, 1, 1), null, null, harness.director(),
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        @DisplayName("a period that has not ended cannot be closed")
        void cannot_close_a_period_that_has_not_ended() {
            harness.base.clock.set(LocalDate.of(2026, 9, 15).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
            assertThatThrownBy(() -> harness.variance.closePeriod(new EnergyCommands.ClosePeriod("MAIN",
                    Utility.ELECTRICITY, LocalDate.of(2026, 9, 1), harness.director(), SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        @DisplayName("managing budgets and tariffs requires FACILITIES_ENERGY_BUDGET_MANAGE")
        void budget_management_refused_without_permission() {
            // The energy officer enters readings but does not manage budgets - that authority sits with the
            // director and the facilities manager (SRS-SFL-S157-02 separation from S157-01's entry role).
            assertThatThrownBy(() -> harness.variance.createBudget(new EnergyCommands.CreateBudget("MAIN",
                    Utility.ELECTRICITY, LocalDate.of(2026, 8, 1), BigDecimal.TEN, null, null, null,
                    harness.base.requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    // =============================================================================================
    // SRS-SFL-S157-03 - Sustainability Reporting and Analytics Publication
    // =============================================================================================

    @Nested
    class SustainabilityKpis {

        /**
         * Before August starts, so {@code CompletenessPolicy.expected} sees a meter that existed for the
         * whole period being reported on - the harness clock otherwise sits at {@code IfimpTestHarness.NOW}
         * (28 September), which would make every meter registered "during" the test look like it did not
         * exist yet in August.
         */
        private static final Instant BEFORE_AUGUST = Instant.parse("2026-07-25T00:00:00Z");

        @org.junit.jupiter.api.BeforeEach
        void travelBeforeAugust() {
            harness.base.clock.set(BEFORE_AUGUST);
        }

        /** Registers, then leaves the clock at {@code at} so a reading dated {@code at} is not "in the future". */
        private EnergyMeter registerThenTravelTo(String code, Utility utility, String site, String building,
                String avamp, String vendorRef, ActorContext actor, Instant at) {
            EnergyMeter meter = harness.meters.register(new EnergyCommands.RegisterMeter(site, building, null, code,
                    code + " meter", utility, MeterSource.AMI, avamp, vendorRef, null, actor, SourceChannel.WEB, null,
                    null));
            harness.base.clock.set(at);
            return meter;
        }

        /** Back to the harness's normal "now", from which August is a fully-ended previous period. */
        private void travelToReportingDate() {
            harness.base.clock.set(gh.edu.clet.sfl.facilities.support.IfimpTestHarness.NOW);
        }

        @Test
        @DisplayName("a KPI always carries its computation period and a data-completeness indicator")
        void kpi_carries_period_and_completeness() {
            registerThenTravelTo("KPI-01", Utility.ELECTRICITY, "MAIN", "LAW", "AVAMP-900", "VENDOR-900",
                    harness.energyOfficer(), Instant.parse("2026-08-15T01:00:00Z"));
            harness.readings.ingest(signed("k1", Map.of("meterRef", "VENDOR-900", "value", "10", "unit", "kWh",
                    "intervalEnd", "2026-08-15T01:00:00Z")), harness.integration());
            travelToReportingDate();

            List<SustainabilityKpi> published = harness.kpis.computeAndPublish(harness.director(), SourceChannel.WEB);

            SustainabilityKpi siteKpi = published.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.SITE && k.siteCode().equals("MAIN")
                            && k.utility() == Utility.ELECTRICITY && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(siteKpi.period()).isNotNull();
            assertThat(siteKpi.completenessFlag()).isNotNull();
            assertThat(siteKpi.expectedReadings()).isGreaterThan(0);
        }

        @Test
        @DisplayName("S157-03: below the configured minimum, a KPI is published with a LOW flag, never withheld")
        void low_completeness_is_published_not_withheld() {
            // A meter expecting hourly readings that received exactly one all month - well under 80%.
            registerThenTravelTo("KPI-02", Utility.WATER, "MAIN", "LAW", "AVAMP-901", "VENDOR-901",
                    harness.energyOfficer(), Instant.parse("2026-08-01T01:00:00Z"));
            harness.readings.ingest(signed("k2", Map.of("meterRef", "VENDOR-901", "value", "1", "unit", "m3",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());
            travelToReportingDate();

            List<SustainabilityKpi> published = harness.kpis.computeAndPublish(harness.director(), SourceChannel.WEB);

            SustainabilityKpi kpi = published.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.SITE && k.utility() == Utility.WATER
                            && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(kpi.completenessFlag()).isEqualTo(gh.edu.clet.sfl.facilities.energy.domain.CompletenessFlag.LOW);
            assertThat(kpi.completenessPct()).isLessThan(new BigDecimal("80"));
        }

        @Test
        @DisplayName("carbon-equivalent is computed only where a factor is configured, and flagged otherwise")
        void carbon_only_where_factor_configured() {
            registerThenTravelTo("KPI-03", Utility.ELECTRICITY, "MAIN", "LAW", "AVAMP-902", "VENDOR-902",
                    harness.energyOfficer(), Instant.parse("2026-08-01T01:00:00Z"));
            harness.readings.ingest(signed("k3", Map.of("meterRef", "VENDOR-902", "value", "100", "unit", "kWh",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());
            travelToReportingDate();

            List<SustainabilityKpi> withoutFactor = harness.kpis.computeAndPublish(harness.director(),
                    SourceChannel.WEB);
            SustainabilityKpi kpiWithoutFactor = withoutFactor.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.SITE && k.utility() == Utility.ELECTRICITY
                            && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(kpiWithoutFactor.carbonKgCo2e()).isNull();
            assertThat(kpiWithoutFactor.emissionFactorStatus())
                    .isEqualTo(SustainabilityKpi.EmissionFactorStatus.NOT_CONFIGURED);

            harness.kpis.createEmissionFactor(new EnergyCommands.CreateEmissionFactor("MAIN", Utility.ELECTRICITY,
                    new BigDecimal("0.5"), LocalDate.of(2026, 1, 1), "national grid factor 2026", harness.director(),
                    SourceChannel.WEB));

            List<SustainabilityKpi> withFactor = harness.kpis.computeAndPublish(harness.director(), SourceChannel.WEB);
            SustainabilityKpi kpiWithFactor = withFactor.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.SITE && k.utility() == Utility.ELECTRICITY
                            && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(kpiWithFactor.carbonKgCo2e()).isEqualByComparingTo("50.0000");
        }

        @Test
        @DisplayName("site/building rollups and the cluster-wide total are all available")
        void rollups_and_cluster_total() {
            EnergyMeter mainMeter = registerThenTravelTo("KPI-04", Utility.ELECTRICITY, "MAIN", "LAW", "AVAMP-903",
                    "VENDOR-903", harness.base.system, Instant.parse("2026-08-01T01:00:00Z"));
            harness.readings.ingest(signed("k4", Map.of("meterRef", "VENDOR-903", "value", "40", "unit", "kWh",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());
            registerThenTravelTo("KPI-05", Utility.ELECTRICITY, "KSI", "KSB", "AVAMP-904", "VENDOR-904",
                    harness.base.system, Instant.parse("2026-08-01T01:00:00Z"));
            harness.readings.ingest(signed("k5", Map.of("meterRef", "VENDOR-904", "value", "60", "unit", "kWh",
                    "intervalEnd", "2026-08-01T01:00:00Z"), "KSI"), harness.integration());
            travelToReportingDate();

            List<SustainabilityKpi> published = harness.kpis.computeAndPublish(harness.base.system, SourceChannel.WEB);

            SustainabilityKpi cluster = published.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.CLUSTER && k.utility() == Utility.ELECTRICITY
                            && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(cluster.siteCode()).isEqualTo(SustainabilityKpi.CLUSTER_SITE);
            assertThat(cluster.consumption()).isEqualByComparingTo("100.0000");

            SustainabilityKpi building = published.stream()
                    .filter(k -> k.scope() == SustainabilityKpi.KpiScope.BUILDING && k.siteCode().equals("MAIN")
                            && k.buildingCode().equals(mainMeter.buildingCode()) && k.utility() == Utility.ELECTRICITY
                            && k.period().start().equals(LocalDate.of(2026, 8, 1)))
                    .findFirst().orElseThrow();
            assertThat(building.consumption()).isEqualByComparingTo("40.0000");
        }

        @Test
        @DisplayName("the cluster rollup is invisible to a single-site caller")
        void cluster_rollup_is_scoped() {
            assertThatThrownBy(() -> harness.kpis.kpis(new EnergyRepository.KpiQuery(
                    SustainabilityKpi.CLUSTER_SITE, null, null, null, null, null, 10), harness.energyOfficer(),
                    SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("published KPIs are queryable from the read model")
        void published_kpis_are_queryable() {
            registerThenTravelTo("KPI-06", Utility.ELECTRICITY, "MAIN", "LAW", "AVAMP-905", "VENDOR-905",
                    harness.energyOfficer(), Instant.parse("2026-08-01T01:00:00Z"));
            harness.readings.ingest(signed("k6", Map.of("meterRef", "VENDOR-905", "value", "5", "unit", "kWh",
                    "intervalEnd", "2026-08-01T01:00:00Z")), harness.integration());
            travelToReportingDate();
            harness.kpis.computeAndPublish(harness.director(), SourceChannel.WEB);

            List<SustainabilityKpi> found = harness.kpis.kpis(new EnergyRepository.KpiQuery("MAIN",
                    SustainabilityKpi.KpiScope.SITE, Utility.ELECTRICITY, EnergyPeriod.PeriodType.MONTH, null, null,
                    50), harness.energyOfficer(), SourceChannel.WEB);

            assertThat(found).isNotEmpty();
            assertThat(harness.base.audit.recorded(AuditAction.SUSTAINABILITY_KPI_PUBLISHED)).isTrue();
            assertThat(harness.base.outbox.published("sfl.ifimp.sustainability-kpi-published.v1")).isTrue();
        }
    }

    // =============================================================================================
    // Health / integration view
    // =============================================================================================

    @Nested
    class IntegrationHealth {

        @Test
        @DisplayName("the health view carries the gate status and never reports the feed as integrated")
        void health_view_reports_gate_status() {
            var health = harness.health.health("MAIN", harness.energyOfficer(), SourceChannel.WEB);
            assertThat(health.gateStatus())
                    .isEqualTo(gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry
                            .GateStatus.SIMULATED_ADAPTER_ONLY);
            assertThat(health.statement()).contains("must not be reported as integrated");
        }

        @Test
        @DisplayName("the health view lists an AMI meter whose device S156 has since enrolled, as a conflict")
        void health_view_lists_device_conflicts() {
            EnergyMeter meter = registerMeter("HLT-01", Utility.ELECTRICITY, MeterSource.AMI, "AVAMP-950",
                    "VENDOR-950");
            harness.devices.enrol("AVAMP-950", "SENSOR-950", "MAIN");

            var health = harness.health.health("MAIN", harness.energyOfficer(), SourceChannel.WEB);

            assertThat(health.deviceConflicts()).anyMatch(conflict -> conflict.meterId().equals(meter.id().toString()));
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private EnergyMeter registerElectricity(String code, MeterSource source, String avamp, String vendorRef) {
        return registerMeter(code, Utility.ELECTRICITY, source, avamp, vendorRef);
    }

    private EnergyMeter registerMeter(String code, Utility utility, MeterSource source, String avamp,
            String vendorRef) {
        return harness.meters.register(new EnergyCommands.RegisterMeter("MAIN", "LAW", null, code, code + " meter",
                utility, source, avamp, vendorRef, null, harness.energyOfficer(), SourceChannel.WEB, null, null));
    }

    private ConsumptionReading enterManual(UUID meterId, String register, Instant at) {
        return enterManualAs(harness.energyOfficer(), meterId, register, at);
    }

    private ConsumptionReading enterManualAs(ActorContext actor, UUID meterId, String register, Instant at) {
        return harness.readings.enterManual(new EnergyCommands.EnterManualReading(meterId, new BigDecimal(register),
                at, null, actor, SourceChannel.WEB, null, null));
    }

    /** One hour's reading, dated so it lands in {@code day}'s bucket (the reading's day is its interval end). */
    private void ingestDailyKwh(EnergyMeter meter, LocalDate day, String kwh) {
        harness.readings.ingest(signed(day + "-key", Map.of("meterRef", meter.vendorMeterRef(), "value", kwh, "unit",
                "kWh", "intervalStart", day + "T00:00:00Z", "intervalEnd", day + "T01:00:00Z")),
                harness.integration());
    }

    /**
     * Signed as of the harness clock's current instant, not the fixed {@link #NOW} used for reading
     * timestamps - {@code VendorMessageVerifier} refuses a signature more than five minutes from when the
     * message is received, and the harness clock stays at {@code IfimpTestHarness.NOW} unless a test moves
     * it, which is unrelated to the historical dates a payload's own fields carry.
     */
    private SignedVendorMessage signed(String key, Map<String, Object> payload) {
        return signed(key, payload, "MAIN");
    }

    private SignedVendorMessage signed(String key, Map<String, Object> payload, String siteCode) {
        Instant signedAt = harness.base.clock.instant();
        String raw = body(payload);
        String signature = VendorMessageVerifier.sign(EnergyTestHarness.AMI_SECRET, signedAt, raw);
        return new SignedVendorMessage(EnergyTestHarness.AMI_SOURCE, "energy.reading", key, siteCode, signedAt,
                signature, raw, payload);
    }

    private static String body(Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return json.append('}').toString();
    }
}
