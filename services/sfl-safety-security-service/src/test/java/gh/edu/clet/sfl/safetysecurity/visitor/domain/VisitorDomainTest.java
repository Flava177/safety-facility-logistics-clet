package gh.edu.clet.sfl.safetysecurity.visitor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Framework-free unit tests for the S160 domain aggregate and its state machine. */
class VisitorDomainTest {

    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

    private VisitorVisit preRegistered(VisitPurpose purpose, boolean watchlistFlagged) {
        return VisitorVisit.preRegister(UUID.randomUUID(), "HQ", "Ama Mensah", "Acme Ltd", "ama@example.com",
                "host-1", "Kojo Host", purpose, NOW.plusSeconds(3600), NOW.plusSeconds(7200), watchlistFlagged,
                "reception", NOW, SourceChannel.WEB, "corr-1");
    }

    @Test
    void a_meeting_requires_host_approval_and_a_delivery_does_not() {
        assertThat(preRegistered(VisitPurpose.MEETING, false).approvalRequired()).isTrue();
        assertThat(preRegistered(VisitPurpose.DELIVERY, false).approvalRequired()).isFalse();
    }

    @Test
    void a_visit_needing_no_approval_confirms_directly() {
        var visit = preRegistered(VisitPurpose.DELIVERY, false);
        var confirmed = visit.confirm(null, null, "reception", NOW, SourceChannel.WEB, "corr-1");
        assertThat(confirmed.status()).isEqualTo(VisitStatus.CONFIRMED);
        assertThat(confirmed.approvalId()).isNull();
    }

    @Test
    void a_visit_needing_approval_cannot_confirm_without_one() {
        var visit = preRegistered(VisitPurpose.MEETING, false);
        assertThatThrownBy(() -> visit.confirm(null, null, "host-1", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL));
    }

    @Test
    void a_watchlist_flagged_visit_cannot_confirm_without_an_override_reason() {
        var visit = preRegistered(VisitPurpose.DELIVERY, true);
        assertThatThrownBy(() -> visit.confirm(null, null, "reception", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_WATCHLIST_MATCH));

        var confirmed = visit.confirm(null, "Cleared by security director", "reception", NOW, SourceChannel.WEB,
                "corr-1");
        assertThat(confirmed.status()).isEqualTo(VisitStatus.CONFIRMED);
        assertThat(confirmed.watchlistOverrideReason()).isEqualTo("Cleared by security director");
    }

    @Test
    void the_full_lifecycle_runs_pre_registered_to_checked_out() {
        var visit = preRegistered(VisitPurpose.DELIVERY, false)
                .confirm(null, null, "reception", NOW, SourceChannel.WEB, "corr-1")
                .assignBadge("B-100", java.util.List.of("LOBBY"), "reception", NOW, SourceChannel.WEB, "corr-1")
                .checkIn("reception", NOW, SourceChannel.WEB, "corr-1")
                .checkOut("reception", NOW, SourceChannel.WEB, "corr-1");
        assertThat(visit.status()).isEqualTo(VisitStatus.CHECKED_OUT);
        assertThat(visit.metadata().version()).isEqualTo(4);
    }

    @Test
    void check_in_requires_a_badge_to_already_be_assigned() {
        var visit = preRegistered(VisitPurpose.DELIVERY, false)
                .confirm(null, null, "reception", NOW, SourceChannel.WEB, "corr-1");
        assertThatThrownBy(() -> visit.checkIn("reception", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_BADGE_NOT_ASSIGNED));
    }

    @Test
    void a_badge_cannot_be_assigned_before_confirmation() {
        var visit = preRegistered(VisitPurpose.DELIVERY, false);
        assertThatThrownBy(() -> visit.assignBadge("B-100", java.util.List.of(), "reception", NOW,
                SourceChannel.WEB, "corr-1")).isInstanceOf(VisitorException.class);
    }

    @Test
    void a_checked_out_visit_cannot_transition_further() {
        var visit = preRegistered(VisitPurpose.DELIVERY, false)
                .confirm(null, null, "reception", NOW, SourceChannel.WEB, "corr-1")
                .assignBadge("B-100", java.util.List.of(), "reception", NOW, SourceChannel.WEB, "corr-1")
                .checkIn("reception", NOW, SourceChannel.WEB, "corr-1")
                .checkOut("reception", NOW, SourceChannel.WEB, "corr-1");
        assertThatThrownBy(() -> visit.checkOut("reception", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_INVALID_STATE_TRANSITION));
    }

    @Test
    void cancellation_requires_a_reason() {
        var visit = preRegistered(VisitPurpose.MEETING, false);
        assertThatThrownBy(() -> visit.cancel(null, "reception", NOW, SourceChannel.WEB, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class);
        var cancelled = visit.cancel("No longer needed", "reception", NOW, SourceChannel.WEB, "corr-1");
        assertThat(cancelled.status()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(cancelled.closureReason()).isEqualTo("No longer needed");
    }

    @Test
    void a_rejection_requires_a_reason_and_a_reason_confirms_it() {
        var visit = preRegistered(VisitPurpose.MEETING, false);
        var rejected = visit.reject(UUID.randomUUID(), "Not available", "host-1", NOW, SourceChannel.WEB, "corr-1");
        assertThat(rejected.status()).isEqualTo(VisitStatus.REJECTED);
        assertThat(rejected.closureReason()).isEqualTo("Not available");
    }
}
