package gh.edu.clet.sfl.emergencynotification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelStatus;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.DeliveryStatus;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationChannel;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordLifecycle;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordMetadata;
import gh.edu.clet.sfl.emergencynotification.domain.model.SiteCode;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import gh.edu.clet.sfl.emergencynotification.domain.policy.BreakGlassPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Framework-free unit tests for the S174 domain aggregates and policies. */
class EmergencyDomainTest {

    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");
    private final RecordMetadata meta = RecordMetadata.createdBy("actor", NOW, SourceChannel.WEB, "corr");

    private NotificationActivation activation(NotificationActivation.Mode mode) {
        return new NotificationActivation(UUID.randomUUID(), "ACT-1", SiteCode.of("HQ"), null, UUID.randomUUID(),
                List.of(UUID.randomUUID()), List.of(), List.of(ChannelType.SMS, ChannelType.EMAIL), mode,
                NotificationActivation.Status.DRAFT, Priority.CRITICAL, null, null, null, null, null, null, null, null,
                null, null, null, null, 0, false, null, null, meta);
    }

    @Test
    void a_template_requires_at_least_one_channel() {
        assertThatThrownBy(() -> new NotificationTemplate(UUID.randomUUID(), "TPL-1", SiteCode.of("HQ"), "t", "b",
                List.of(), true, RecordLifecycle.ACTIVE, meta)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void routine_activation_runs_the_full_approval_gated_lifecycle() {
        var a = activation(NotificationActivation.Mode.ROUTINE)
                .submit(meta).approve("director", meta).activate(meta).allClear(meta);
        assertThat(a.status()).isEqualTo(NotificationActivation.Status.ALL_CLEAR_PENDING);
        var closed = a.close("resolved", "sent=2", "ack=1", UUID.randomUUID(), meta);
        assertThat(closed.status()).isEqualTo(NotificationActivation.Status.CLOSED);
    }

    @Test
    void a_routine_activation_cannot_activate_before_approval() {
        assertThatThrownBy(() -> activation(NotificationActivation.Mode.ROUTINE).submit(meta).activate(meta))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void break_glass_sends_without_pre_approval() {
        var a = activation(NotificationActivation.Mode.BREAK_GLASS).breakGlassActivate(meta);
        assertThat(a.status()).isEqualTo(NotificationActivation.Status.BREAK_GLASS_ACTIVE);
        assertThat(a.mode()).isEqualTo(NotificationActivation.Mode.BREAK_GLASS);
    }

    @Test
    void break_glass_closure_is_blocked_until_after_action_approval() {
        var live = activation(NotificationActivation.Mode.BREAK_GLASS).breakGlassActivate(meta).allClear(meta);
        assertThatThrownBy(() -> live.close("done", "sent=2", "ack=1", UUID.randomUUID(), meta))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("after-the-fact");
        var approved = live.afterActionApprove("director", "declared emergency", meta);
        assertThat(approved.close("done", "sent=2", "ack=1", UUID.randomUUID(), meta).status())
                .isEqualTo(NotificationActivation.Status.CLOSED);
    }

    @Test
    void closure_requires_reason_summary_and_evidence() {
        var ready = activation(NotificationActivation.Mode.ROUTINE).submit(meta).approve("d", meta).activate(meta)
                .allClear(meta);
        assertThatThrownBy(() -> ready.close("done", "sent=2", "ack=1", null, meta))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ready.close("done", null, "ack=1", UUID.randomUUID(), meta))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void all_clear_is_only_valid_from_an_active_state() {
        assertThatThrownBy(() -> activation(NotificationActivation.Mode.ROUTINE).allClear(meta))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void break_glass_policy_requires_an_eligible_template_or_scenario() {
        assertThat(BreakGlassPolicy.eligible(true, false)).isTrue();
        assertThatThrownBy(() -> BreakGlassPolicy.requireEligible(false, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void channel_counters_reflect_delivery_and_acknowledgement() {
        var channel = new NotificationChannel(UUID.randomUUID(), UUID.randomUUID(), SiteCode.of("HQ"), ChannelType.SMS,
                ChannelStatus.SENDING, 2, 2, 0, 0, 0, meta);
        var delivered = channel.recordDelivery(DeliveryStatus.DELIVERED, meta).recordDelivery(DeliveryStatus.FAILED, meta);
        assertThat(delivered.deliveredCount()).isEqualTo(1);
        assertThat(delivered.failedCount()).isEqualTo(1);
        assertThat(delivered.status()).isEqualTo(ChannelStatus.PARTIALLY_DELIVERED);
        assertThat(channel.recordAcknowledgement(meta).acknowledgedCount()).isEqualTo(1);
    }
}
