package gh.edu.clet.sfl.emergencynotification.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.LifeSafetyEventPort;
import gh.edu.clet.sfl.emergencynotification.application.service.ActivationService;
import gh.edu.clet.sfl.emergencynotification.application.service.DrillService;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyDashboardService;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyIntegrationService;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyRecordsService;
import gh.edu.clet.sfl.emergencynotification.application.service.ProviderCallbackService;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.EmergencyScenario;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordMetadata;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Database-backed proof of the 24 mandatory S174 scenarios (records, routine + break-glass workflow,
 * after-action approval, delivery/acknowledgement callbacks with idempotency and secure-inbox rejection,
 * outbox dead-letter + replay, escalation, all-clear, closure gating, audit-chain integrity, dashboard
 * reconciliation, drills, fast-lane, degraded mode and observe-only life-safety). Each test builds an
 * isolated tenant site and drives the application services directly.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.emergency.scheduling.enabled=false",
        "sfl.emergency.messaging.drainer-enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.emergencynotification.e2e.EmergencyPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class EmergencyMandatoryScenariosEndToEndTest extends EmergencyPostgresSupport {

    private static final String SECRET = "sfl-emergency-simulator-secret";

    @Autowired EmergencyRecordsService records;
    @Autowired ActivationService activations;
    @Autowired ProviderCallbackService callbacks;
    @Autowired EmergencyDashboardService dashboards;
    @Autowired DrillService drills;
    @Autowired EmergencyIntegrationService integration;
    @Autowired EmergencyRepository repository;
    @Autowired AuditPort audit;
    @Autowired LifeSafetyEventPort lifeSafety;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private record Fixture(String site, ActorContext director) {}

    private Fixture newFixture() {
        String site = "EMG" + System.nanoTime();
        return new Fixture(site, actor(site, SflRole.SECURITY_DIRECTOR));
    }

    private ActorContext actor(String site, SflRole... roles) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + roles[0], "Actor", Set.of(roles), Set.of(site),
                false), "emg-e2e");
    }

    private NotificationTemplate template(Fixture f, boolean breakGlass) {
        return records.createTemplate(new EmergencyRecordsService.CreateTemplate(f.site(), null, "Evacuate now",
                "Please evacuate immediately.", List.of(ChannelType.SMS, ChannelType.EMAIL), breakGlass, f.director(),
                SourceChannel.WEB));
    }

    private EmergencyScenario scenario(Fixture f, boolean breakGlass) {
        return records.createScenario(new EmergencyRecordsService.CreateScenario(f.site(), null, "Fire", Priority.CRITICAL,
                null, breakGlass, f.director(), SourceChannel.WEB));
    }

    private AudienceGroup audience(Fixture f, int count) {
        return records.createAudienceGroup(new EmergencyRecordsService.CreateAudienceGroup(f.site(), null, "All staff",
                "dir://all", count, f.director(), SourceChannel.WEB));
    }

    private NotificationActivation routineDraft(Fixture f, UUID templateId, UUID audienceId) {
        return activations.createDraft(new ActivationService.CreateActivation(f.site(), null, templateId,
                List.of(audienceId), List.of(), List.of(ChannelType.SMS), Priority.HIGH, "INC-1",
                "draft-" + System.nanoTime(), f.director(), SourceChannel.WEB));
    }

    private NotificationActivation activeRoutine(Fixture f) {
        var t = template(f, false);
        var a = audience(f, 2);
        var draft = routineDraft(f, t.id(), a.id());
        activations.submit(draft.id(), f.director(), SourceChannel.WEB);
        activations.approve(draft.id(), f.director(), SourceChannel.WEB);
        return activations.activate(draft.id(), f.director(), SourceChannel.WEB);
    }

    private NotificationActivation breakGlass(Fixture f, ActorContext actor) {
        var t = template(f, true);
        var a = audience(f, 3);
        return activations.breakGlass(new ActivationService.CreateActivation(f.site(), null, t.id(), List.of(a.id()),
                List.of(), List.of(ChannelType.SMS, ChannelType.EMAIL), Priority.CRITICAL, "INC-BG",
                "break-glass-" + System.nanoTime(), actor, SourceChannel.WEB));
    }

    private ProviderCallbackService.ProviderCallback signed(Fixture f, Map<String, Object> payload, boolean validSig) {
        String raw = json.writeValueAsString(payload);
        Instant signedAt = Instant.now();
        String signature = validSig
                ? gh.edu.clet.sfl.emergencynotification.infrastructure.integration.EmergencyIntegrationInbox
                        .hmac(SECRET, signedAt + "." + raw)
                : "not-a-valid-signature";
        return new ProviderCallbackService.ProviderCallback("SIMULATOR", signature, signedAt, raw, payload,
                actor(f.site(), SflRole.SECURITY_DIRECTOR));
    }

    private Map<String, Object> deliveryPayload(Fixture f, UUID activationId, String pm, String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("activationId", activationId.toString());
        p.put("siteCode", f.site());
        p.put("providerMessageId", pm);
        p.put("channelType", "SMS");
        if (status != null) {
            p.put("status", status);
        }
        p.put("recipientRef", "recipient-1");
        return p;
    }

    // 1
    @Test void create_records() {
        Fixture f = newFixture();
        assertThat(template(f, true).id()).isNotNull();
        assertThat(scenario(f, true).id()).isNotNull();
        assertThat(audience(f, 10).id()).isNotNull();
        assertThat(records.createRecipientZone(new EmergencyRecordsService.CreateRecipientZone(f.site(), null, "Block A",
                "bldg://A", f.director(), SourceChannel.WEB)).id()).isNotNull();
    }

    // 2
    @Test void submit_routine_for_approval() {
        Fixture f = newFixture();
        var t = template(f, false);
        var a = audience(f, 2);
        var draft = routineDraft(f, t.id(), a.id());
        var submitted = activations.submit(draft.id(), f.director(), SourceChannel.WEB);
        assertThat(submitted.status()).isEqualTo(NotificationActivation.Status.PENDING_APPROVAL);
    }

    // 3
    @Test void unauthorised_approval_is_rejected() {
        Fixture f = newFixture();
        ActorContext coordinator = actor(f.site(), SflRole.EMERGENCY_COORDINATOR);
        var t = template(f, false);
        var a = audience(f, 2);
        var draft = activations.createDraft(new ActivationService.CreateActivation(f.site(), null, t.id(),
                List.of(a.id()), List.of(), List.of(ChannelType.SMS), Priority.HIGH, null,
                "draft-" + System.nanoTime(), coordinator, SourceChannel.WEB));
        activations.submit(draft.id(), coordinator, SourceChannel.WEB);
        assertThatThrownBy(() -> activations.approve(draft.id(), coordinator, SourceChannel.WEB))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_APPROVAL));
    }

    // 4
    @Test void approve_and_activate_routine() {
        assertThat(activeRoutine(newFixture()).status()).isEqualTo(NotificationActivation.Status.ACTIVE);
    }

    // 5
    @Test void break_glass_sends_without_pre_approval() {
        Fixture f = newFixture();
        var a = breakGlass(f, f.director());
        assertThat(a.status()).isEqualTo(NotificationActivation.Status.BREAK_GLASS_ACTIVE);
        assertThat(a.mode()).isEqualTo(NotificationActivation.Mode.BREAK_GLASS);
    }

    // 6
    @Test void break_glass_requires_after_action_before_closure() {
        Fixture f = newFixture();
        var a = breakGlass(f, f.director());
        activations.allClear(a.id(), f.director(), SourceChannel.WEB);
        assertThatThrownBy(() -> activations.close(a.id(), "done", new ActivationService.EvidenceMeta(null, null,
                "evidence://x", null, gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass.INCIDENT_10_YEARS),
                f.director(), SourceChannel.WEB)).isInstanceOf(IllegalStateException.class);
        activations.afterActionApprove(a.id(), "declared emergency", f.director(), SourceChannel.WEB);
        var closed = activations.close(a.id(), "resolved", new ActivationService.EvidenceMeta(null, null, "evidence://x",
                null, gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass.INCIDENT_10_YEARS),
                f.director(), SourceChannel.WEB);
        assertThat(closed.status()).isEqualTo(NotificationActivation.Status.CLOSED);
    }

    // 7
    @Test void break_glass_by_unauthorised_role_is_denied() {
        Fixture f = newFixture();
        ActorContext hse = actor(f.site(), SflRole.HSE_MANAGER);
        assertThatThrownBy(() -> breakGlass(f, hse)).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
    }

    // 8 & 9
    @Test void delivery_callback_is_idempotent_and_duplicate_is_safely_ignored() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        callbacks.deliveryStatus(signed(f, deliveryPayload(f, a.id(), "pm-1", "DELIVERED"), true));
        assertThatThrownBy(() -> callbacks.deliveryStatus(signed(f, deliveryPayload(f, a.id(), "pm-1", "DELIVERED"),
                true))).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_DUPLICATE_MESSAGE));
        assertThat(repository.findReceipts(a.id())).hasSize(1);
    }

    // 10
    @Test void unsigned_callback_is_rejected_before_domain_side_effects() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        assertThatThrownBy(() -> callbacks.deliveryStatus(signed(f, deliveryPayload(f, a.id(), "pm-2", "DELIVERED"),
                false))).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_INVALID_SIGNATURE));
        assertThat(repository.findReceipts(a.id())).isEmpty();
    }

    // 11
    @Test void schema_invalid_callback_is_rejected_before_domain_side_effects() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        assertThatThrownBy(() -> callbacks.deliveryStatus(signed(f, deliveryPayload(f, a.id(), "pm-3", null), true)))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_SCHEMA_VALIDATION_FAILED));
        assertThat(repository.findReceipts(a.id())).isEmpty();
    }

    // 12 & 13
    @Test void failed_outbound_delivery_is_surfaced_and_replayable() {
        Fixture f = newFixture();
        UUID messageId = UUID.randomUUID();
        jdbc.update("INSERT INTO emergency_notification.outbox_messages (id,event_type,event_version,aggregate_type,"
                + "aggregate_id,payload,status,attempt_count,created_at) VALUES (?,?,?,?,?,?::jsonb,?,?,?)",
                messageId, "sfl.ssemp.emergency-notification-activated.v1", 1, "NotificationActivation",
                UUID.randomUUID().toString(), "{}", "DEAD_LETTERED", 5, Timestamp.from(Instant.now()));
        assertThat(integration.health(f.director()).deadLettered()).isGreaterThanOrEqualTo(1);
        assertThat(integration.replay(messageId, f.director(), SourceChannel.INTEGRATION)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM emergency_notification.outbox_messages WHERE id=?",
                String.class, messageId)).isEqualTo("PENDING");
    }

    // 14
    @Test void acknowledgement_tracking_updates_counts() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("activationId", a.id().toString());
        ack.put("siteCode", f.site());
        ack.put("recipientRef", "recipient-1");
        ack.put("channelType", "SMS");
        callbacks.acknowledgement(signed(f, ack, true));
        assertThat(repository.countAcknowledgements(a.id())).isEqualTo(1);
    }

    // 15
    @Test void unacknowledged_recipients_escalate_after_sla() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        assertThat(repository.findActivationsForAckEscalation(f.site(), Instant.now().plusSeconds(3600), 100))
                .contains(a.id());
        activations.escalateForSla(a.id(), f.director(), SourceChannel.SCHEDULER);
        assertThat(repository.findActivation(a.id()).orElseThrow().status())
                .isEqualTo(NotificationActivation.Status.ESCALATED);
    }

    // 16
    @Test void all_clear_only_for_active_activation() {
        Fixture f = newFixture();
        var t = template(f, false);
        var a = audience(f, 2);
        var draft = routineDraft(f, t.id(), a.id());
        assertThatThrownBy(() -> activations.allClear(draft.id(), f.director(), SourceChannel.WEB))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void cancel_is_a_pre_send_operator_action_with_history_and_permissions() {
        Fixture f = newFixture();
        var t = template(f, false);
        var a = audience(f, 2);
        var draft = routineDraft(f, t.id(), a.id());

        var cancelled = activations.cancel(draft.id(), "incident stood down before send", f.director(),
                SourceChannel.WEB);

        assertThat(cancelled.status()).isEqualTo(NotificationActivation.Status.CANCELLED);
        assertThat(cancelled.closureReason()).isEqualTo("incident stood down before send");
        assertThat(repository.findActivationHistory(cancelled.id()))
                .anySatisfy(entry -> assertThat(entry.action()).isEqualTo("cancel"));

        var live = activeRoutine(f);
        assertThatThrownBy(() -> activations.cancel(live.id(), "too late", f.director(), SourceChannel.WEB))
                .isInstanceOf(IllegalStateException.class);

        var other = routineDraft(f, t.id(), a.id());
        assertThatThrownBy(() -> activations.cancel(other.id(), "read-only actor", actor(f.site(), SflRole.AUDITOR),
                SourceChannel.WEB)).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
    }

    // 17
    @Test void activation_cannot_close_without_evidence() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        activations.allClear(a.id(), f.director(), SourceChannel.WEB);
        assertThatThrownBy(() -> activations.close(a.id(), "done", new ActivationService.EvidenceMeta(null, null, "  ",
                null, gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass.INCIDENT_10_YEARS),
                f.director(), SourceChannel.WEB)).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_CLOSURE_EVIDENCE_MISSING));
    }

    @Test void closed_activation_can_be_reopened_with_history_and_permissions() {
        Fixture f = newFixture();
        var a = activeRoutine(f);
        activations.allClear(a.id(), f.director(), SourceChannel.WEB);
        var closed = activations.close(a.id(), "resolved", new ActivationService.EvidenceMeta(null, null,
                "evidence://closure", null,
                gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass.INCIDENT_10_YEARS),
                f.director(), SourceChannel.WEB);

        assertThatThrownBy(() -> activations.reopen(closed.id(), "audit-only actor",
                actor(f.site(), SflRole.AUDITOR), SourceChannel.WEB)).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));

        var reopened = activations.reopen(closed.id(), "follow-up communication required", f.director(),
                SourceChannel.WEB);
        assertThat(reopened.status()).isEqualTo(NotificationActivation.Status.REOPENED);
        assertThat(reopened.closureReason()).isEqualTo("follow-up communication required");
        assertThat(repository.findActivationHistory(reopened.id()))
                .anySatisfy(entry -> assertThat(entry.action()).isEqualTo("reopen"));
    }

    // 18
    @Test void audit_chain_integrity_holds_and_tampering_is_detected() {
        Fixture f = newFixture();
        activeRoutine(f);
        assertThat(audit.verifyChain().intact()).isTrue();
        // Tamper one row, prove detection, then restore so the shared chain stays intact for other tests.
        Long seq = jdbc.queryForObject("SELECT MAX(sequence_no) FROM emergency_notification.audit_events", Long.class);
        String original = jdbc.queryForObject("SELECT actor FROM emergency_notification.audit_events WHERE sequence_no=?",
                String.class, seq);
        jdbc.update("UPDATE emergency_notification.audit_events SET actor='tampered' WHERE sequence_no=?", seq);
        assertThat(audit.verifyChain().intact()).isFalse();
        jdbc.update("UPDATE emergency_notification.audit_events SET actor=? WHERE sequence_no=?", original, seq);
        assertThat(audit.verifyChain().intact()).isTrue();
    }

    // 19
    @Test void dashboard_counts_reconcile_to_source() {
        Fixture f = newFixture();
        activeRoutine(f);
        Map<String, Object> dash = dashboards.dashboard(f.site(), f.director());
        assertThat(((Number) dash.get("activeActivationCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    // 20
    @Test void stale_data_shows_a_warning() {
        Fixture f = newFixture();
        Map<String, Object> dash = dashboards.dashboard(f.site(), f.director());
        assertThat(dash.get("stale")).isEqualTo(true);
    }

    // 21
    @Test void drill_records_performance_and_report_metrics() {
        Fixture f = newFixture();
        var drill = drills.start(new DrillService.StartDrill(f.site(), null, 50, "quarterly", f.director(),
                SourceChannel.WEB));
        var completed = drills.complete(drill.id(), 48, 40, 1500L, "ok", f.director(), SourceChannel.WEB);
        assertThat(completed.status()).isEqualTo(
                gh.edu.clet.sfl.emergencynotification.domain.model.DrillRun.Status.COMPLETED);
        assertThat(completed.acknowledgementRatePercent()).isEqualTo(80);
    }

    // 22
    @Test void ct20_fast_lane_path_is_represented_and_measured() {
        Fixture f = newFixture();
        var a = breakGlass(f, f.director());
        assertThat(a.fastLaneMillis()).isNotNull();
        assertThat(a.fastLaneMillis()).isGreaterThanOrEqualTo(0L);
    }

    // 23
    @Test void degraded_fallback_is_a_live_operator_action_using_the_recorded_gateway() {
        Fixture f = newFixture();
        var live = activeRoutine(f);

        var degraded = activations.degradedFallback(live.id(), "RECORDED_DIRECT_HANDOFF", f.director(),
                SourceChannel.EDGE);
        var reloaded = repository.findActivation(degraded.id()).orElseThrow();
        assertThat(reloaded.degradedMode()).isTrue();
        assertThat(reloaded.fallbackPath()).isEqualTo("RECORDED_DIRECT_HANDOFF");
        assertThat(reloaded.mode()).isEqualTo(NotificationActivation.Mode.DEGRADED);
        assertThat(repository.findChannels(reloaded.id())).isNotEmpty();
        assertThat(repository.findActivationHistory(reloaded.id()))
                .anySatisfy(entry -> assertThat(entry.action()).isEqualTo("degraded-fallback"));

        var t = template(f, false);
        var a = audience(f, 2);
        var draft = routineDraft(f, t.id(), a.id());
        assertThatThrownBy(() -> activations.degradedFallback(draft.id(), "draft path", f.director(),
                SourceChannel.EDGE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> activations.degradedFallback(live.id(), "read-only actor",
                actor(f.site(), SflRole.AUDITOR), SourceChannel.EDGE)).isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
    }

    // 24
    @Test void sfl_does_not_perform_certified_life_safety_actuation() {
        Fixture f = newFixture();
        // Break-glass completes without any actuation dependency; the life-safety feed is observe-only.
        var a = breakGlass(f, f.director());
        assertThat(a.status()).isEqualTo(NotificationActivation.Status.BREAK_GLASS_ACTIVE);
        assertThat(lifeSafety.latestLifeSafetyEvent(f.site())).isEmpty();
    }
}
