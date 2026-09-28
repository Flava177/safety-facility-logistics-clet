package gh.edu.clet.sfl.facilities.phase2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorInboxPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorRejectionRecorder;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorSourcePort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VerifiedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import gh.edu.clet.sfl.facilities.support.MutableClock;
import gh.edu.clet.sfl.facilities.support.RecordingAuditPort;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NFR-SEC2 for IFIMP: "forged or malformed messages are rejected and logged, never actioned (verified
 * by a forged-message rejection test per system)". This is the shared half; each of S156, S157 and
 * S173 adds its own forged-message test on its own endpoint.
 */
class VendorMessageVerifierTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final String SECRET = "test-secret";
    private static final String BODY = "{\"deviceId\":\"AHU-1\",\"value\":\"21.5\"}";

    private final MutableClock clock = new MutableClock(NOW);
    private final RecordingAuditPort audit = new RecordingAuditPort(NOW);
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();
    private final List<SecurityEvent> siem = new ArrayList<>();
    private final FakeInbox inbox = new FakeInbox();
    private final ActorContext integration = TestDoubles.actor("integration.bms",
            Set.of(SflRole.SERVICE_INTEGRATION), "*");
    private VendorMessageVerifier verifier;

    @BeforeEach
    void setUp() {
        VendorSourcePort sources = sourceId -> "BMS-A".equals(sourceId)
                ? Optional.of(new VendorSourcePort.VendorSource("BMS-A", VendorChannel.BMS_TELEMETRY, SECRET,
                        Set.of("MAIN")))
                : Optional.empty();
        SecurityEventForwarderPort forwarder = event -> {
            siem.add(event);
            return new SecurityEventForwarderPort.ForwardResult("TEST", false);
        };
        verifier = new VendorMessageVerifier(sources, inbox,
                new VendorRejectionRecorder(inbox, audit, forwarder, outbox, clock), clock);
    }

    @Test
    @DisplayName("a correctly signed message from an allowlisted source is accepted and claimed")
    void accepted() {
        VerifiedVendorMessage verified = accept(message("BMS-A", "MAIN", NOW, sign(NOW, BODY), "k1"));

        assertThat(verified.duplicate()).isFalse();
        assertThat(verified.siteCode()).isEqualTo("MAIN");
        assertThat(inbox.accepted).contains("BMS-A|k1");
    }

    @Test
    @DisplayName("a forged signature is rejected, logged, audited, sent to the SIEM and published - and not accepted")
    void forged_signature() {
        assertRejected(message("BMS-A", "MAIN", NOW, "00ff", "k1"), VendorRejectionReason.SIGNATURE_INVALID);
        assertThat(audit.recorded(AuditAction.VENDOR_MESSAGE_REJECTED)).isTrue();
        assertThat(siem).singleElement().extracting(SecurityEvent::category).isEqualTo("VENDOR_MESSAGE_REJECTED");
        assertThat(outbox.published("sfl.integration.vendor-message-rejected.v1")).isTrue();
        assertThat(inbox.accepted).isEmpty();
    }

    @Test
    @DisplayName("an unknown source is rejected before anything else is believed")
    void unknown_source() {
        assertRejected(message("NOBODY", "MAIN", NOW, sign(NOW, BODY), "k1"), VendorRejectionReason.SOURCE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("a source may not post on a channel it is not registered for")
    void wrong_channel() {
        SignedVendorMessage message = message("BMS-A", "MAIN", NOW, sign(NOW, BODY), "k1");
        assertThatThrownBy(() -> verifier.accept(message, VendorChannel.ENERGY_METERING, List.of(), "S157",
                integration)).isInstanceOf(FacilitiesException.class);
        assertThat(inbox.rejections).containsExactly(VendorRejectionReason.CHANNEL_NOT_PERMITTED);
    }

    @Test
    @DisplayName("a correctly signed message replayed outside the window is rejected")
    void stale_timestamp() {
        Instant old = NOW.minus(Duration.ofMinutes(6));
        assertRejected(message("BMS-A", "MAIN", old, sign(old, BODY), "k1"),
                VendorRejectionReason.TIMESTAMP_OUTSIDE_WINDOW);
    }

    @Test
    @DisplayName("an authenticated source may not report for a site it is not registered for")
    void other_site() {
        assertRejected(message("BMS-A", "KSI", NOW, sign(NOW, BODY), "k1"), VendorRejectionReason.SITE_NOT_PERMITTED);
    }

    @Test
    @DisplayName("a message missing a field the module cannot act without is rejected as malformed")
    void missing_field() {
        SignedVendorMessage message = message("BMS-A", "MAIN", NOW, sign(NOW, BODY), "k1");
        assertThatThrownBy(() -> verifier.accept(message, VendorChannel.BMS_TELEMETRY, List.of("channel"), "S156",
                integration)).isInstanceOf(FacilitiesException.class);
        assertThat(inbox.rejections).containsExactly(VendorRejectionReason.SCHEMA_INVALID);
    }

    @Test
    @DisplayName("the same idempotency key again is a duplicate, recorded and not re-actioned")
    void duplicate() {
        accept(message("BMS-A", "MAIN", NOW, sign(NOW, BODY), "k1"));
        VerifiedVendorMessage again = accept(message("BMS-A", "MAIN", NOW, sign(NOW, BODY), "k1"));

        assertThat(again.duplicate()).isTrue();
    }

    @Test
    @DisplayName("every rejection answers with the same code, whichever check failed")
    void uniform_refusal() {
        assertThatThrownBy(() -> accept(message("NOBODY", "MAIN", NOW, "x", "k1")))
                .isInstanceOfSatisfying(FacilitiesException.class,
                        e -> assertThat(e.code()).isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));
        assertThatThrownBy(() -> accept(message("BMS-A", "MAIN", NOW, "x", "k1")))
                .isInstanceOfSatisfying(FacilitiesException.class,
                        e -> assertThat(e.code()).isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));
    }

    private void assertRejected(SignedVendorMessage message, VendorRejectionReason reason) {
        assertThatThrownBy(() -> accept(message)).isInstanceOf(FacilitiesException.class);
        assertThat(inbox.rejections).containsExactly(reason);
    }

    private VerifiedVendorMessage accept(SignedVendorMessage message) {
        return verifier.accept(message, VendorChannel.BMS_TELEMETRY, List.of("deviceId"), "S156", integration);
    }

    private static SignedVendorMessage message(String source, String site, Instant signedAt, String signature,
            String key) {
        return new SignedVendorMessage(source, "telemetry.reading", key, site, signedAt, signature, BODY,
                Map.of("deviceId", "AHU-1", "value", "21.5"));
    }

    private static String sign(Instant at, String body) {
        return VendorMessageVerifier.sign(SECRET, at, body);
    }

    private static final class FakeInbox implements VendorInboxPort {
        final Set<String> accepted = new HashSet<>();
        final List<VendorRejectionReason> rejections = new ArrayList<>();

        @Override
        public boolean alreadyAccepted(String sourceId, String idempotencyKey) {
            return accepted.contains(sourceId + "|" + idempotencyKey);
        }

        @Override
        public UUID recordAccepted(InboxEntry entry) {
            accepted.add(entry.sourceId() + "|" + entry.idempotencyKey());
            return UUID.randomUUID();
        }

        @Override
        public UUID recordDuplicate(InboxEntry entry) {
            return UUID.randomUUID();
        }

        @Override
        public UUID recordRejected(InboxEntry entry, VendorRejectionReason reason, String detail) {
            rejections.add(reason);
            return UUID.randomUUID();
        }
    }
}
