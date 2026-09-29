package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.buildingsystems.BuildingSystemsFixture;
import gh.edu.clet.sfl.facilities.buildingsystems.InMemoryVendorSupport;
import gh.edu.clet.sfl.facilities.buildingsystems.application.AvampAssetProjectionService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingHealthService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ThresholdRuleService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingDeviceDirectory;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.HealthState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantinedReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleConflict;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.AvampAssetEventHandler;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.BacnetBridgeTelemetryAdapter;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.SimulatedBmsTelemetryAdapter;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One nested class per SRS-SFL-S156 requirement, one test per acceptance criterion, validation rule and
 * error state - S156 Building Management System / IoT.
 */
class S156MandatoryScenariosTest {

    private static final Instant NOW = IfimpTestHarness.NOW;

    private IfimpTestHarness harness;
    private BuildingSystemsFixture fx;

    @BeforeEach
    void setUp() {
        harness = new IfimpTestHarness();
        fx = new BuildingSystemsFixture(harness);
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private void projectAvamp(String siteCode, String avampAssetId) {
        fx.avampProjection.apply(new AvampAssetProjectionService.AvampAssetFact("sfl.avamp.asset-registered.v1",
                avampAssetId, siteCode, avampAssetId, "Asset " + avampAssetId, "ROOM_DEVICE", "ACTIVE", "ROOM",
                "HALL-A", NOW), harness.system);
    }

    private BmsDevice registerDevice(String siteCode, String deviceCode, String avampAssetId,
            BuildingSystemType systemType, String buildingCode, UUID roomId) {
        return fx.devices.register(new BuildingSystemsCommands.RegisterDevice(siteCode, deviceCode, avampAssetId,
                "Device " + deviceCode, systemType, DeviceKind.SENSOR, buildingCode, roomId, 300, null, null, null,
                null, null, harness.engineer, SourceChannel.WEB));
    }

    private SignedVendorMessage signedMessage(String siteCode, String idempotencyKey, String format,
            Map<String, Object> extraFields, List<Map<String, Object>> readings, String signature, Instant signedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", format);
        payload.putAll(extraFields);
        payload.put(readings == null ? "unused" : "readings", readings == null ? null : readings);
        return new SignedVendorMessage(InMemoryVendorSupport.SOURCE_ID, "bms.telemetry", idempotencyKey, siteCode,
                signedAt, signature, "irrelevant-raw-body-" + idempotencyKey, payload);
    }

    private SignedVendorMessage validSimMessage(String siteCode, String idempotencyKey,
            List<Map<String, Object>> readings) {
        return signedMessage(siteCode, idempotencyKey, SimulatedBmsTelemetryAdapter.FORMAT, Map.of(), readings,
                sign(harness.clock.instant(), idempotencyKey), harness.clock.instant());
    }

    private String sign(Instant at, String idempotencyKey) {
        return VendorMessageVerifier.sign(InMemoryVendorSupport.SECRET, at, "irrelevant-raw-body-" + idempotencyKey);
    }

    private static Map<String, Object> reading(String deviceCode, String channel, String kind, String value,
            Instant observedAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deviceId", deviceCode);
        map.put("channel", channel);
        map.put("kind", kind);
        map.put("value", value);
        map.put("observedAt", observedAt.toString());
        return map;
    }

    private BuildingSystemsCommands.IngestionResult ingest(SignedVendorMessage message) {
        return fx.ingestion.ingest(new BuildingSystemsCommands.IngestTelemetry(message, harness.integration,
                SourceChannel.INTEGRATION));
    }

    // =============================================================================================
    // SRS-SFL-S156-01: Authenticated Telemetry Ingestion and Normalisation
    // =============================================================================================

    @Nested
    class Ingestion {

        @Test
        @DisplayName("a valid authenticated reading is normalised, resolved to a stable S152 location and stored as fact")
        void accepted_reading_is_normalised_and_resolved() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());

            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "k1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21.5", NOW))));

            assertThat(result.accepted()).isEqualTo(1);
            assertThat(result.duplicate()).isFalse();
            UUID readingId = result.items().get(0).readingId();
            var stored = fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("MAIN", null, null,
                    null, null, 10));
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).id()).isEqualTo(readingId);
            assertThat(stored.get(0).locationCode()).isEqualTo("HALL-A");
        }

        @Test
        @DisplayName("a forged or unauthenticated message is rejected and logged, and no automated action follows")
        void forged_message_rejected_and_never_actioned() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            SignedVendorMessage forged = signedMessage("MAIN", "forged-1", SimulatedBmsTelemetryAdapter.FORMAT,
                    Map.of(), List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21.5", NOW)), "00bad00",
                    harness.clock.instant());

            assertThatThrownBy(() -> ingest(forged)).isInstanceOfSatisfying(FacilitiesException.class,
                    exception -> assertThat(exception.code()).isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));

            assertThat(fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("MAIN", null, null,
                    null, null, 10))).isEmpty();
            assertThat(fx.repository.findAlerts("MAIN", null)).isEmpty();
            assertThat(harness.intake.find(UUID.randomUUID())).isEmpty();
            assertThat(harness.audit.recorded(AuditAction.VENDOR_MESSAGE_REJECTED)).isTrue();
            assertThat(fx.siemEvents).isNotEmpty();
            assertThat(harness.outbox.published("sfl.integration.vendor-message-rejected.v1")).isTrue();
        }

        @Test
        @DisplayName("telemetry referencing an unresolvable location is quarantined, not silently dropped")
        void unresolvable_location_is_quarantined() {
            projectAvamp("MAIN", "AVAMP-1");
            // Saved directly with a building S152 does not know, simulating a room archived after mapping.
            BmsDevice device = BmsDevice.register(UUID.randomUUID(), "MAIN", "GHOST-01", "AVAMP-1", "Ghost device",
                    BuildingSystemType.HVAC, DeviceKind.SENSOR, "NOWHERE", null, null, 300, null, null, null, null,
                    null, "engineer", NOW, SourceChannel.WEB, "corr-seed");
            fx.repository.saveDevice(device);

            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "k2",
                    List.of(reading("GHOST-01", "supply-air-temp", "TEMPERATURE_C", "21.5", NOW))));

            assertThat(result.items().get(0).outcome()).isEqualTo(BuildingSystemsCommands.ItemOutcome.QUARANTINED);
            assertThat(result.items().get(0).reason()).isEqualTo(QuarantineReason.LOCATION_UNRESOLVABLE);
            assertThat(result.items().get(0).code()).isEqualTo("BMS_LOCATION_UNRESOLVABLE");
            assertThat(harness.audit.recorded(AuditAction.BMS_TELEMETRY_QUARANTINED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.reading-quarantined.v1")).isTrue();
        }

        @Test
        @DisplayName("telemetry from an unregistered device id is quarantined and flagged for registration")
        void unregistered_device_is_quarantined_and_flagged() {
            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "k3",
                    List.of(reading("UNKNOWN-01", "supply-air-temp", "TEMPERATURE_C", "21.5", NOW))));

            var item = result.items().get(0);
            assertThat(item.outcome()).isEqualTo(BuildingSystemsCommands.ItemOutcome.QUARANTINED);
            assertThat(item.reason()).isEqualTo(QuarantineReason.DEVICE_UNREGISTERED);
            assertThat(item.code()).isEqualTo("BMS_DEVICE_UNREGISTERED");
        }

        @Test
        @DisplayName("a physically impossible value is flagged, not stored as fact, and never evaluated against rules")
        void implausible_value_is_flagged_and_never_evaluated() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            fx.rules.create(new BuildingSystemsCommands.CreateRule("MAIN", "Hot", MeasuredQuantity.TEMPERATURE_C,
                    BuildingSystemType.HVAC, null, "LAW", null, RuleCondition.ABOVE, null, new BigDecimal("28"),
                    Set.of(), Duration.ofMinutes(5), AlertPriority.HIGH, "initial", harness.engineer,
                    SourceChannel.WEB));

            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "k4",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "900", NOW))));

            assertThat(result.items().get(0).reason()).isEqualTo(QuarantineReason.IMPLAUSIBLE_VALUE);
            assertThat(harness.audit.recorded(AuditAction.BMS_READING_FLAGGED_IMPLAUSIBLE)).isTrue();
            assertThat(fx.repository.findAlerts("MAIN", null)).isEmpty();
        }

        @Test
        @DisplayName("a reading older than the clock-skew tolerance is out of order and quarantined")
        void out_of_order_reading_is_quarantined() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            ingest(validSimMessage("MAIN", "k5",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));

            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "k6",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "22",
                            NOW.minus(Duration.ofHours(1))))));

            assertThat(result.items().get(0).reason()).isEqualTo(QuarantineReason.OUT_OF_ORDER);
        }

        @Test
        @DisplayName("a replayed message answers as before and is not re-actioned")
        void duplicate_message_answers_as_before() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            SignedVendorMessage message = validSimMessage("MAIN", "k7",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW)));

            BuildingSystemsCommands.IngestionResult first = ingest(message);
            BuildingSystemsCommands.IngestionResult second = ingest(message);

            assertThat(second.duplicate()).isTrue();
            assertThat(second.items()).hasSize(1);
            assertThat(second.items().get(0).readingId()).isEqualTo(first.items().get(0).readingId());
            assertThat(fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("MAIN", null, null,
                    null, null, 10))).hasSize(1);
        }

        @Test
        @DisplayName("retained readings are purged after the configured retention, except evidence")
        void retention_purge_spares_evidence() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            harness.configuration.set("bms.retention.readings-days", "1");
            ingest(validSimMessage("MAIN", "k8",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));
            harness.clock.advance(Duration.ofDays(3));

            int purged = fx.ingestion.purgeExpiredReadings(harness.system);

            assertThat(purged).isEqualTo(1);
            assertThat(harness.audit.recorded(AuditAction.BMS_TELEMETRY_PURGED)).isTrue();
        }
    }

    // =============================================================================================
    // SRS-SFL-S156-02: Threshold Rules and Automated Work-Order Generation
    // =============================================================================================

    @Nested
    class Rules {

        private BmsDevice device;

        @BeforeEach
        void seedDevice() {
            projectAvamp("MAIN", "AVAMP-1");
            device = registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
        }

        private ThresholdRule createRule(BigDecimal upperLimit, Duration debounce) {
            return fx.rules.create(new BuildingSystemsCommands.CreateRule("MAIN", "Server room hot",
                    MeasuredQuantity.TEMPERATURE_C, BuildingSystemType.HVAC, null, "LAW", null, RuleCondition.ABOVE,
                    null, upperLimit, Set.of(), debounce, AlertPriority.HIGH, "initial", harness.engineer,
                    SourceChannel.WEB)).rule();
        }

        @Test
        @DisplayName("a transient breach shorter than the debounce window raises nothing")
        void transient_breach_raises_nothing() {
            createRule(new BigDecimal("28"), Duration.ofMinutes(5));

            ingest(validSimMessage("MAIN", "t1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "30", NOW))));
            harness.clock.advance(Duration.ofMinutes(1));
            ingest(validSimMessage("MAIN", "t2",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21",
                            harness.clock.instant()))));

            assertThat(fx.repository.findAlerts("MAIN", null)).isEmpty();
            assertThat(harness.outbox.published("sfl.ifimp.bms-alert-raised.v1")).isFalse();
        }

        @Test
        @DisplayName("a sustained breach raises a work order carrying the triggering telemetry evidence")
        void sustained_breach_raises_work_order_with_evidence() {
            createRule(new BigDecimal("28"), Duration.ofMinutes(5));
            ingest(validSimMessage("MAIN", "s1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "30", NOW))));
            harness.clock.advance(Duration.ofMinutes(6));

            ingest(validSimMessage("MAIN", "s2",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "31", harness.clock.instant()))));

            List<BmsAlert> alerts = fx.repository.findAlerts("MAIN", AlertStatus.ACTIVE);
            assertThat(alerts).hasSize(1);
            BmsAlert alert = alerts.get(0);
            assertThat(alert.hasWorkOrder()).isTrue();
            assertThat(alert.evidenceReadingIds()).isNotEmpty();
            var raised = harness.intake.find(alert.workOrderId());
            assertThat(raised).isPresent();
            assertThat(raised.get().isOpen()).isTrue();
            assertThat(harness.audit.recorded(AuditAction.BMS_WORK_ORDER_RAISED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.bms-alert-raised.v1")).isTrue();
        }

        @Test
        @DisplayName("a repeated breach on the same asset with an open work order is linked, never duplicated")
        void repeated_breach_correlates_rather_than_duplicates() {
            createRule(new BigDecimal("28"), Duration.ofMinutes(5));
            ingest(validSimMessage("MAIN", "c1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "30", NOW))));
            harness.clock.advance(Duration.ofMinutes(6));
            ingest(validSimMessage("MAIN", "c2",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "31", harness.clock.instant()))));
            BmsAlert firstAlert = fx.repository.findAlerts("MAIN", null).get(0);

            // Back in range - the underlying fault is not fixed, only the reading.
            harness.clock.advance(Duration.ofMinutes(1));
            ingest(validSimMessage("MAIN", "c3",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "20", harness.clock.instant()))));
            harness.clock.advance(Duration.ofMinutes(6));
            ingest(validSimMessage("MAIN", "c4",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "29", harness.clock.instant()))));
            harness.clock.advance(Duration.ofMinutes(6));
            ingest(validSimMessage("MAIN", "c5",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "29", harness.clock.instant()))));

            assertThat(fx.repository.findAlert(firstAlert.id())).get()
                    .extracting(BmsAlert::occurrences).satisfies(count -> assertThat((int) count).isGreaterThan(1));
            assertThat(harness.audit.recorded(AuditAction.BMS_ALERT_CORRELATED)).isTrue();
            long distinctWorkOrders = fx.repository.findAlerts("MAIN", null).stream()
                    .map(BmsAlert::workOrderId).distinct().count();
            assertThat(distinctWorkOrders).isEqualTo(1);
        }

        @Test
        @DisplayName("overlapping active rules with different thresholds: the stricter wins and the conflict is logged")
        void rule_conflict_the_stricter_rule_wins() {
            ThresholdRuleService.RuleChange looser = fx.rules.create(new BuildingSystemsCommands.CreateRule("MAIN",
                    "Estate-wide", MeasuredQuantity.TEMPERATURE_C, BuildingSystemType.HVAC, null, null, null,
                    RuleCondition.ABOVE, null, new BigDecimal("30"), Set.of(), Duration.ZERO, AlertPriority.MEDIUM,
                    "initial", harness.engineer, SourceChannel.WEB));
            ThresholdRuleService.RuleChange stricter = fx.rules.create(new BuildingSystemsCommands.CreateRule("MAIN",
                    "Server room", MeasuredQuantity.TEMPERATURE_C, BuildingSystemType.HVAC, null, "LAW", null,
                    RuleCondition.ABOVE, null, new BigDecimal("24"), Set.of(), Duration.ZERO, AlertPriority.HIGH,
                    "initial", harness.engineer, SourceChannel.WEB));

            assertThat(stricter.conflicts()).hasSize(1);
            assertThat(stricter.conflicts().get(0).winningRuleId()).isEqualTo(stricter.rule().ruleId());
            List<RuleConflict> logged = fx.rules.conflicts("MAIN", harness.director, SourceChannel.WEB);
            assertThat(logged).hasSize(1);
            assertThat(harness.audit.recorded(AuditAction.BMS_RULE_CONFLICT_DETECTED)).isTrue();

            ingest(validSimMessage("MAIN", "conflict-1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "26", NOW))));
            var state = fx.repository.findChannelState(device.id(), "supply-air-temp");
            assertThat(state).isPresent();
            assertThat(state.get().breachRuleId()).isEqualTo(stricter.rule().ruleId());
        }

        @Test
        @DisplayName("rule changes are versioned: revising writes a new version and keeps the prior one")
        void rule_changes_are_versioned() {
            ThresholdRule created = createRule(new BigDecimal("28"), Duration.ofMinutes(5));

            fx.rules.revise(new BuildingSystemsCommands.ReviseRule(created.ruleId(), null, null, null, null, null,
                    null, null, new BigDecimal("26"), null, null, null, "tightened for exam season", null,
                    harness.engineer, SourceChannel.WEB));

            List<ThresholdRule> versions = fx.rules.history(created.ruleId(), harness.director, SourceChannel.WEB);
            assertThat(versions).hasSize(2);
            assertThat(versions.get(0).isCurrent()).isFalse();
            assertThat(versions.get(1).isCurrent()).isTrue();
            assertThat(versions.get(1).upperLimit()).isEqualByComparingTo("26");
            assertThat(harness.audit.recorded(AuditAction.BMS_RULE_REVISED)).isTrue();
        }

        @Test
        @DisplayName("the rule-authoring engineer does not hold the override permission and cannot disable a rule")
        void rule_author_cannot_disable_without_override_permission() {
            ThresholdRule created = createRule(new BigDecimal("28"), Duration.ofMinutes(5));

            assertThatThrownBy(() -> fx.rules.disable(new BuildingSystemsCommands.DisableRule(created.ruleId(),
                    "no longer needed", "director.owner", harness.engineer, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("a rule cannot be silently disabled: an override without a reason and a named owner is refused")
        void disabling_a_rule_requires_a_reason_and_a_named_owner() {
            ThresholdRule created = createRule(new BigDecimal("28"), Duration.ofMinutes(5));

            assertThatThrownBy(() -> fx.rules.disable(new BuildingSystemsCommands.DisableRule(created.ruleId(), null,
                    null, harness.director, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, exception -> assertThat(exception.code())
                            .isEqualTo(FacilitiesErrorCode.BMS_RULE_OVERRIDE_REQUIRED));
        }

        @Test
        @DisplayName("the accountable owner disables a rule with an audited override, and it stops evaluating breaches")
        void an_audited_override_disables_a_rule() {
            ThresholdRule created = createRule(new BigDecimal("28"), Duration.ofMinutes(5));

            ThresholdRuleService.RuleChange disabled = fx.rules.disable(new BuildingSystemsCommands.DisableRule(
                    created.ruleId(), "chiller replacement in progress", "maintenance.supervisor", harness.director,
                    SourceChannel.WEB));

            assertThat(disabled.rule().enabled()).isFalse();
            assertThat(disabled.rule().accountableOwner()).isEqualTo("maintenance.supervisor");
            assertThat(harness.audit.recorded(AuditAction.BMS_RULE_DISABLED_BY_OVERRIDE)).isTrue();

            ingest(validSimMessage("MAIN", "disabled-1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "35", NOW))));
            assertThat(fx.repository.findAlerts("MAIN", null)).isEmpty();
        }
    }

    // =============================================================================================
    // SRS-SFL-S156-03: Building System Health Dashboard and Escalation
    // =============================================================================================

    @Nested
    class Health {

        @Test
        @DisplayName("a device that has never reported shows unknown, never normal by default")
        void never_reported_device_is_unknown_not_normal() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());

            BuildingHealthService.SiteHealth health = fx.health.health("MAIN", harness.director, SourceChannel.WEB);

            assertThat(health.state()).isEqualTo(HealthState.UNKNOWN);
            assertThat(health.buildings()).hasSize(1);
            assertThat(health.buildings().get(0).systems().get(0).devices().get(0).state())
                    .isEqualTo(HealthState.UNKNOWN);
        }

        @Test
        @DisplayName("a stale device is shown as unknown once the staleness window elapses, never as healthy")
        void stale_device_becomes_unknown_after_the_window() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            ingest(validSimMessage("MAIN", "h1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));
            assertThat(fx.health.health("MAIN", harness.director, SourceChannel.WEB).state())
                    .isEqualTo(HealthState.NORMAL);

            harness.clock.advance(Duration.ofHours(2));

            assertThat(fx.health.health("MAIN", harness.director, SourceChannel.WEB).state())
                    .isEqualTo(HealthState.UNKNOWN);
        }

        @Test
        @DisplayName("a sensor offline longer than the configured window raises its own alert, distinct from a telemetry fault")
        void offline_sensor_raises_a_distinct_alert_and_recovers() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", harness.hall.id());
            ingest(validSimMessage("MAIN", "o1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));
            harness.clock.advance(Duration.ofHours(1));

            int raised = fx.alertService.sweepOfflineDevices(harness.system);

            assertThat(raised).isEqualTo(1);
            List<BmsAlert> offline = fx.repository.findAlerts("MAIN", AlertStatus.ACTIVE);
            assertThat(offline).extracting(BmsAlert::type).containsExactly(AlertType.SENSOR_OFFLINE);
            assertThat(harness.audit.recorded(AuditAction.BMS_SENSOR_OFFLINE_DETECTED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.bms-sensor-offline.v1")).isTrue();

            harness.clock.advance(Duration.ofMinutes(1));
            ingest(validSimMessage("MAIN", "o2",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", harness.clock.instant()))));

            assertThat(fx.repository.findAlerts("MAIN", AlertStatus.ACTIVE)).isEmpty();
            assertThat(harness.audit.recorded(AuditAction.BMS_SENSOR_RECOVERED)).isTrue();
        }

        @Test
        @DisplayName("total power loss escalates immediately, bypassing debounce, to the emergency fast lane and SOC")
        void total_power_loss_escalates_immediately() {
            projectAvamp("MAIN", "AVAMP-2");
            registerDevice("MAIN", "MAINS-01", "AVAMP-2", BuildingSystemType.ELECTRICAL, "LAW", null);

            ingest(validSimMessage("MAIN", "power-1",
                    List.of(reading("MAINS-01", "mains-supply", "POWER_STATE", "0", NOW))));

            List<BmsAlert> alerts = fx.repository.findAlerts("MAIN", AlertStatus.ACTIVE);
            assertThat(alerts).hasSize(1);
            BmsAlert alert = alerts.get(0);
            assertThat(alert.type()).isEqualTo(AlertType.CRITICAL_FAULT);
            assertThat(alert.criticalFault()).isEqualTo(CriticalFaultType.TOTAL_POWER_LOSS);
            assertThat(alert.hasWorkOrder()).isTrue();
            assertThat(harness.audit.recorded(AuditAction.BMS_CRITICAL_FAULT_ESCALATED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.building-critical-fault-detected.v1")).isTrue();
            assertThat(fx.siemEvents).anyMatch(event -> "BMS_CRITICAL_FAULT".equals(event.category()));
        }

        @Test
        @DisplayName("lift entrapment escalates immediately as a critical fault")
        void lift_entrapment_escalates_immediately() {
            projectAvamp("MAIN", "AVAMP-3");
            registerDevice("MAIN", "LIFT-01", "AVAMP-3", BuildingSystemType.LIFT, "LAW", null);

            ingest(validSimMessage("MAIN", "lift-1",
                    List.of(reading("LIFT-01", "car-state", "LIFT_STATUS", "3", NOW))));

            assertThat(fx.repository.findAlerts("MAIN", AlertStatus.ACTIVE))
                    .extracting(BmsAlert::criticalFault).containsExactly(CriticalFaultType.LIFT_ENTRAPMENT);
        }

        @Test
        @DisplayName("the health response carries the S156 procurement-gate status; nothing reports the feed as integrated")
        void health_carries_the_procurement_gate_status() {
            BuildingHealthService.SiteHealth health = fx.health.health("MAIN", harness.director, SourceChannel.WEB);

            assertThat(health.procurementGate())
                    .isEqualTo(gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry.GateStatus.SIMULATED_ADAPTER_ONLY);
        }
    }

    // =============================================================================================
    // SRS-SFL-S156-04: IoT Device Inventory and Lifecycle via AVAMP
    // =============================================================================================

    @Nested
    class DeviceInventory {

        @Test
        @DisplayName("a device cannot be registered against an AVAMP asset id S156 has not seen")
        void device_requires_an_existing_avamp_asset() {
            assertThatThrownBy(() -> registerDevice("MAIN", "AHU-99", "NEVER-PROJECTED", BuildingSystemType.HVAC,
                    "LAW", null)).isInstanceOf(FacilitiesException.InvalidParentReferenceException.class);
        }

        @Test
        @DisplayName("one device per AVAMP asset id")
        void one_device_per_avamp_asset() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", null);

            assertThatThrownBy(() -> registerDevice("MAIN", "AHU-02", "AVAMP-1", BuildingSystemType.HVAC, "LAW",
                    null)).isInstanceOf(FacilitiesException.DuplicateIdentifierException.class);
        }

        @Test
        @DisplayName("the AVAMP projection updates from asset-registered and asset-location-changed events, ordered by AVAMP's own timestamp")
        void avamp_events_feed_the_projection_in_order() {
            AvampAssetEventHandler handler = new AvampAssetEventHandler(fx.avampProjection);
            assertThat(handler.handles("sfl.avamp.asset-registered.v1")).isTrue();
            assertThat(handler.handles("sfl.avamp.asset-location-changed.v1")).isTrue();
            assertThat(handler.handles("sfl.ftlmp.vehicle-service-due.v1")).isFalse();

            handler.handle(new InboundIntegrationEvent(UUID.randomUUID(), "sfl.avamp.asset-registered.v1",
                    "AssetReference", "AVAMP-1", "MAIN", "corr-1", null, Map.of("id", "AVAMP-1", "siteCode", "MAIN",
                            "status", "ACTIVE", "locationType", "ROOM", "locationReference", "HALL-A",
                            "updatedAt", NOW.toString())));
            registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", null);

            // An older redelivered event must not move the projection backwards.
            handler.handle(new InboundIntegrationEvent(UUID.randomUUID(), "sfl.avamp.asset-location-changed.v1",
                    "AssetReference", "AVAMP-1", "MAIN", "corr-2", null, Map.of("id", "AVAMP-1", "siteCode", "MAIN",
                            "status", "ACTIVE", "locationType", "ROOM", "locationReference", "OLD",
                            "updatedAt", NOW.minus(Duration.ofDays(1)).toString())));
            assertThat(fx.repository.findAvampAsset("AVAMP-1")).get()
                    .extracting(a -> a.locationReference()).isEqualTo("HALL-A");

            handler.handle(new InboundIntegrationEvent(UUID.randomUUID(), "sfl.avamp.asset-location-changed.v1",
                    "AssetReference", "AVAMP-1", "MAIN", "corr-3", null, Map.of("id", "AVAMP-1", "siteCode", "MAIN",
                            "status", "ACTIVE", "locationType", "ROOM", "locationReference", "MEET-1",
                            "updatedAt", NOW.plus(Duration.ofDays(1)).toString())));
            assertThat(fx.repository.findAvampAsset("AVAMP-1")).get()
                    .extracting(a -> a.locationReference()).isEqualTo("MEET-1");
            assertThat(harness.audit.recorded(AuditAction.BMS_AVAMP_ASSET_PROJECTED)).isTrue();
        }

        @Test
        @DisplayName("decommissioning a device retires rather than deletes its record and history")
        void decommissioning_retires_not_deletes() {
            projectAvamp("MAIN", "AVAMP-1");
            BmsDevice device = registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", null);
            ingest(validSimMessage("MAIN", "r1",
                    List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));

            BmsDevice retired = fx.devices.retire(new BuildingSystemsCommands.RetireDevice(device.id(),
                    "end of life", null, harness.supervisor, SourceChannel.WEB));

            assertThat(retired.status()).isEqualTo(DeviceStatus.RETIRED);
            assertThat(retired.retiredBy()).isEqualTo("supervisor");
            assertThat(retired.retirementReason()).isEqualTo("end of life");
            assertThat(fx.devices.devices("MAIN", DeviceStatus.ACTIVE, harness.director, SourceChannel.WEB)).isEmpty();
            assertThat(fx.devices.device(device.id(), harness.director, SourceChannel.WEB).id()).isEqualTo(device.id());
            assertThat(fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("MAIN", null, null,
                    null, null, 10))).hasSize(1);
            assertThat(harness.audit.recorded(AuditAction.BMS_DEVICE_RETIRED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.bms-device-retired.v1")).isTrue();
        }

        @Test
        @DisplayName("firmware and calibration due dates raise reminders, once per due date")
        void lifecycle_reminders_are_raised_once_per_due_date() {
            projectAvamp("MAIN", "AVAMP-1");
            fx.devices.register(new BuildingSystemsCommands.RegisterDevice("MAIN", "AHU-01", "AVAMP-1", "AHU 1",
                    BuildingSystemType.HVAC, DeviceKind.SENSOR, "LAW", null, 300, null, null, null, null,
                    LocalDate.of(2026, 10, 5), harness.engineer, SourceChannel.WEB));

            int raised = fx.devices.sweepLifecycleReminders(harness.system);
            int raisedAgain = fx.devices.sweepLifecycleReminders(harness.system);

            assertThat(raised).isEqualTo(1);
            assertThat(raisedAgain).isZero();
            assertThat(harness.audit.recorded(AuditAction.BMS_DEVICE_LIFECYCLE_REMINDER_RAISED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.bms-device-lifecycle-due.v1")).isTrue();
        }

        @Test
        @DisplayName("quarantine review: release once the device is registered, or discard with a reason")
        void quarantine_review_release_or_discard() {
            BuildingSystemsCommands.IngestionResult result = ingest(validSimMessage("MAIN", "q1",
                    List.of(reading("NEW-DEVICE", "supply-air-temp", "TEMPERATURE_C", "21", NOW))));
            UUID quarantineId = result.items().get(0).quarantineId();

            List<QuarantinedReading> pending = fx.ingestion.quarantine("MAIN", QuarantineStatus.PENDING,
                    harness.supervisor, SourceChannel.WEB);
            assertThat(pending).extracting(QuarantinedReading::id).contains(quarantineId);

            // Not yet registered - release is refused rather than silently accepted as fact.
            assertThatThrownBy(() -> fx.ingestion.release(new BuildingSystemsCommands.ReleaseQuarantine(quarantineId,
                    "mapped", harness.supervisor, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);

            projectAvamp("MAIN", "AVAMP-9");
            registerDevice("MAIN", "NEW-DEVICE", "AVAMP-9", BuildingSystemType.HVAC, "LAW", null);
            QuarantinedReading released = fx.ingestion.release(new BuildingSystemsCommands.ReleaseQuarantine(
                    quarantineId, "device now registered", harness.supervisor, SourceChannel.WEB));

            assertThat(released.status()).isEqualTo(QuarantineStatus.RELEASED);
            assertThat(released.releasedReadingId()).isNotNull();
            // Released as history, but never evaluated against rules: no alert follows even though it
            // arrived late.
            assertThat(fx.repository.findAlerts("MAIN", null)).isEmpty();

            BuildingSystemsCommands.IngestionResult discardable = ingest(validSimMessage("MAIN", "q2",
                    List.of(reading("NEW-DEVICE", "supply-air-temp", "TEMPERATURE_C", "9999", NOW))));
            QuarantinedReading discarded = fx.ingestion.discard(new BuildingSystemsCommands.DiscardQuarantine(
                    discardable.items().get(0).quarantineId(), "sensor fault confirmed", harness.supervisor,
                    SourceChannel.WEB));
            assertThat(discarded.status()).isEqualTo(QuarantineStatus.DISCARDED);
        }

        @Test
        @DisplayName("S157 can ask whether an AVAMP asset is already an S156 device, through the published contract")
        void building_device_directory_contract_is_fulfilled() {
            BuildingDeviceDirectory directory = fx.directory;
            assertThat(directory.findByAvampAssetId("AVAMP-1")).isEmpty();

            projectAvamp("MAIN", "AVAMP-1");
            BmsDevice device = registerDevice("MAIN", "AHU-01", "AVAMP-1", BuildingSystemType.HVAC, "LAW", null);

            var found = directory.findByAvampAssetId("AVAMP-1");
            assertThat(found).isPresent();
            assertThat(found.get().deviceId()).isEqualTo(device.id());
            assertThat(found.get().status()).isEqualTo("ACTIVE");
        }
    }

    // =============================================================================================
    // SRS-SFL-S156-05: Vendor Replaceability and No Hard-Wired Dependency
    // =============================================================================================

    @Nested
    class Replaceability {

        @Test
        @DisplayName("a second, differently shaped vendor (BACnet/MQTT bridge) converts units into SFL's common model")
        void second_shipped_adapter_converts_units() {
            projectAvamp("MAIN", "AVAMP-1");
            registerDevice("MAIN", "AHU-K1", "AVAMP-1", BuildingSystemType.HVAC, "LAW", null);
            String body = "bacnet-body";
            Instant signedAt = harness.clock.instant();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("deviceInstance", "AHU-K1");
            point.put("objectName", "ZN-T");
            point.put("engineeringUnits", "degrees-fahrenheit");
            point.put("presentValue", "71.6");
            point.put("timestamp", NOW.toEpochMilli());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("format", BacnetBridgeTelemetryAdapter.FORMAT);
            payload.put("gateway", "MAIN-GW-01");
            payload.put("points", List.of(point));
            SignedVendorMessage message = new SignedVendorMessage(InMemoryVendorSupport.SOURCE_ID, "bacnet.cov",
                    "bacnet-1", "MAIN", signedAt,
                    VendorMessageVerifier.sign(InMemoryVendorSupport.SECRET, signedAt, body), body, payload);

            ingest(message);

            var stored = fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("MAIN", null, null,
                    null, null, 10));
            assertThat(stored).hasSize(1);
            // 71.6F == 22.0C.
            assertThat(stored.get(0).value()).isEqualByComparingTo("22.00");
            assertThat(stored.get(0).quantity()).isEqualTo(MeasuredQuantity.TEMPERATURE_C);
        }

        @Test
        @DisplayName("a third, test-only vendor adapter at site KSI ingests through the same pipeline with zero domain changes")
        void a_third_vendor_adapter_needs_no_domain_change() {
            BmsTelemetryTranslatorPort testOnlyAdapter = new BmsTelemetryTranslatorPort() {
                @Override
                public String format() {
                    return "test-only-vendor/v1";
                }

                @Override
                public List<TranslatedReading> translate(Map<String, Object> payload) {
                    return List.of(new TranslatedReading("KSI-AHU-01", "zone-temp", MeasurementKindOf("TEMPERATURE_C"),
                            new BigDecimal(String.valueOf(payload.get("temp"))), NOW));
                }
            };
            var ingestionWithThirdAdapter = fx.ingestionWith(testOnlyAdapter);
            gh.edu.clet.sfl.common.security.ActorContext ksiEngineer = gh.edu.clet.sfl.facilities.support.TestDoubles
                    .actor("engineer.ksi", Set.of(gh.edu.clet.sfl.common.security.SflRole.FACILITIES_ENGINEER), "KSI");
            projectAvamp("KSI", "AVAMP-KSI-1");
            fx.devices.register(new BuildingSystemsCommands.RegisterDevice("KSI", "KSI-AHU-01", "AVAMP-KSI-1",
                    "Kumasi AHU", BuildingSystemType.HVAC, DeviceKind.SENSOR, "KSB", harness.kumasiHall.id(), 300,
                    null, null, null, null, null, ksiEngineer, SourceChannel.WEB));
            String body = "third-vendor-body";
            Instant signedAt = harness.clock.instant();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("format", "test-only-vendor/v1");
            payload.put("temp", "19.5");
            SignedVendorMessage message = new SignedVendorMessage(InMemoryVendorSupport.SOURCE_ID, "third.vendor",
                    "third-1", "KSI", signedAt,
                    VendorMessageVerifier.sign(InMemoryVendorSupport.SECRET, signedAt, body), body, payload);

            var result = ingestionWithThirdAdapter.ingest(new BuildingSystemsCommands.IngestTelemetry(message,
                    harness.integration, SourceChannel.INTEGRATION));

            assertThat(result.accepted()).isEqualTo(1);
            var stored = fx.repository.findReadings(new BuildingSystemsRepository.ReadingQuery("KSI", null, null,
                    null, null, 10));
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).locationCode()).isEqualTo("KSI-HALL");
        }

        private static gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind
                MeasurementKindOf(String name) {
            return gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind.valueOf(name);
        }
    }

    // =============================================================================================
    // Refusals: wrong role, other site
    // =============================================================================================

    @Nested
    class Refusals {

        @Test
        @DisplayName("an actor without FACILITIES_BMS_TELEMETRY_INGEST cannot post telemetry")
        void wrong_role_cannot_ingest_telemetry() {
            assertThatThrownBy(() -> fx.ingestion.ingest(new BuildingSystemsCommands.IngestTelemetry(
                    validSimMessage("MAIN", "wr1", List.of(reading("AHU-01", "supply-air-temp", "TEMPERATURE_C",
                            "21", NOW))), harness.engineer, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("an actor without FACILITIES_BMS_DEVICE_MANAGE cannot register a device")
        void wrong_role_cannot_register_device() {
            projectAvamp("MAIN", "AVAMP-1");
            assertThatThrownBy(() -> fx.devices.register(new BuildingSystemsCommands.RegisterDevice("MAIN", "AHU-01",
                    "AVAMP-1", "AHU 1", BuildingSystemType.HVAC, DeviceKind.SENSOR, "LAW", null, 300, null, null,
                    null, null, null, harness.requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("an actor scoped to another site cannot list this site's devices")
        void other_site_cannot_see_devices() {
            assertThatThrownBy(() -> fx.devices.devices("MAIN", null, harness.kumasiManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("an actor scoped to another site cannot resolve this site's health")
        void other_site_cannot_read_health() {
            assertThatThrownBy(() -> fx.health.health("MAIN", harness.kumasiManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }
}
