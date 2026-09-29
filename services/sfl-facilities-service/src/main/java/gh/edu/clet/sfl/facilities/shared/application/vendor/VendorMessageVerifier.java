package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.PlatformThreads;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authenticated front door for every inbound vendor message IFIMP accepts - SRS 2026/002 NFR-SEC2,
 * SRS-SFL-S156-01, S157-01, S173-01.
 *
 * <p>Mirrors the checks SSEMP's {@code LifeSafetyIntegrationInbox} and {@code AccessControlIntegrationInbox}
 * established, in the same order and for the same reasons, once for this service rather than per module:
 *
 * <ol>
 *   <li><strong>Source allowlist</strong> - an unknown source is refused before its bytes are hashed.</li>
 *   <li><strong>Channel</strong> - a source is registered for one feed; a BMS gateway's key cannot post a
 *       meter reading or an event hand-off.</li>
 *   <li><strong>Timestamp window</strong> - stops a captured message being replayed tomorrow.</li>
 *   <li><strong>HMAC-SHA256</strong> over {@code signedAt + "." + rawPayload}, compared in constant time.
 *       Over the raw bytes, never a re-serialisation.</li>
 *   <li><strong>Site</strong> - the source may report only for the sites it is registered for.</li>
 *   <li><strong>Schema</strong> - the envelope fields and the caller's required payload fields.</li>
 *   <li><strong>Idempotency</strong> - an accepted key seen again is a duplicate, answered and not
 *       re-actioned.</li>
 * </ol>
 *
 * <p>Every refusal is recorded by {@link VendorRejectionRecorder} - inbox, hash chain, SIEM, outbox - in
 * a transaction of its own and under the platform scope, and only then thrown. The thrown message is
 * the same for every reason: the sender learns that it failed, not which check failed.
 *
 * <p>This class knows nothing about telemetry, meters or events. A module passes the channel and the
 * payload fields it cannot do without, and receives a {@link VerifiedVendorMessage} or an exception.
 * Deeper, module-specific validation that fails afterwards calls {@link #reject} so it is recorded the
 * same way.
 */
@Service
public class VendorMessageVerifier {

    private final VendorSourcePort sources;
    private final VendorInboxPort inbox;
    private final VendorRejectionRecorder rejections;
    private final Clock clock;

    public VendorMessageVerifier(VendorSourcePort sources, VendorInboxPort inbox,
            VendorRejectionRecorder rejections, Clock clock) {
        this.sources = sources;
        this.inbox = inbox;
        this.rejections = rejections;
        this.clock = clock;
    }

    /**
     * Verifies and, when it passes, claims the message in the caller's transaction.
     *
     * @param module the SRS system accepting it, recorded on a rejection - {@code S156}
     * @param requiredPayloadFields fields the module cannot act without; absence is a schema rejection
     */
    @Transactional
    public VerifiedVendorMessage accept(SignedVendorMessage message, VendorChannel channel,
            Collection<String> requiredPayloadFields, String module, ActorContext actor) {
        Instant receivedAt = clock.instant();
        String sourceId = normalise(message.sourceId());
        String site = message.siteCode() == null || message.siteCode().isBlank() ? null
                : EstateCodes.normalize(message.siteCode());
        String hash = sha256(message.rawPayload() == null ? "" : message.rawPayload());
        VendorInboxPort.InboxEntry entry = new VendorInboxPort.InboxEntry(sourceId == null ? "UNKNOWN" : sourceId,
                channel, message.messageType(), message.idempotencyKey(), null, hash, message.signedAt(),
                receivedAt, actor == null ? null : actor.correlationId());

        Optional<VendorSourcePort.VendorSource> source = sourceId == null ? Optional.empty()
                : sources.find(sourceId);
        if (source.isEmpty()) {
            throw rejected(entry, VendorRejectionReason.SOURCE_NOT_ALLOWED, "Source is not allowlisted.", module,
                    actor);
        }
        if (source.get().channel() != channel) {
            throw rejected(entry, VendorRejectionReason.CHANNEL_NOT_PERMITTED,
                    "Source is registered for " + source.get().channel() + ".", module, actor);
        }
        if (message.signature() == null || message.signature().isBlank() || message.signedAt() == null) {
            throw rejected(entry, VendorRejectionReason.SIGNATURE_MISSING, "No signature or signing time.", module,
                    actor);
        }
        Duration window = sources.signatureWindow();
        if (Duration.between(message.signedAt(), receivedAt).abs().compareTo(window) > 0) {
            throw rejected(entry, VendorRejectionReason.TIMESTAMP_OUTSIDE_WINDOW,
                    "Signed at " + message.signedAt() + ", outside the " + window + " window.", module, actor);
        }
        String expected = sign(source.get().secret(), message.signedAt(), message.rawPayload());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                message.signature().strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw rejected(entry, VendorRejectionReason.SIGNATURE_INVALID, "HMAC mismatch.", module, actor);
        }

        // Authenticated from here on: the site the message claims can now be recorded against it.
        VendorInboxPort.InboxEntry authenticated = withSite(entry, site);
        if (site == null || !source.get().mayReportFor(site)) {
            throw rejected(entry, VendorRejectionReason.SITE_NOT_PERMITTED,
                    "Source may not report for site " + site + ".", module, actor);
        }
        if (message.idempotencyKey() == null || message.idempotencyKey().isBlank()
                || message.messageType() == null || message.messageType().isBlank()) {
            throw rejected(authenticated, VendorRejectionReason.SCHEMA_INVALID,
                    "Envelope requires messageType and idempotencyKey.", module, actor);
        }
        Map<String, Object> payload = message.payload() == null ? Map.of() : message.payload();
        for (String field : requiredPayloadFields) {
            Object value = payload.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                throw rejected(authenticated, VendorRejectionReason.SCHEMA_INVALID,
                        "Missing required field '" + field + "'.", module, actor);
            }
        }

        boolean duplicate = inbox.alreadyAccepted(authenticated.sourceId(), message.idempotencyKey());
        UUID id = duplicate ? inbox.recordDuplicate(authenticated) : inbox.recordAccepted(authenticated);
        return new VerifiedVendorMessage(id, authenticated.sourceId(), channel, message.messageType(),
                message.idempotencyKey(), site, Map.copyOf(payload), hash, receivedAt, duplicate);
    }

    /**
     * Rejects an already-authenticated message that failed a module's own validation - an impossible
     * value type, an unknown channel code. Recorded exactly as a verifier rejection is, then thrown.
     */
    public FacilitiesException reject(VerifiedVendorMessage message, String detail, String module,
            ActorContext actor) {
        return rejected(new VendorInboxPort.InboxEntry(message.sourceId(), message.channel(), message.messageType(),
                message.idempotencyKey(), message.siteCode(), message.payloadHash(), null, clock.instant(),
                actor == null ? null : actor.correlationId()), VendorRejectionReason.SCHEMA_INVALID, detail, module,
                actor);
    }

    /** The signature a sender must produce. Public so simulators and tests sign exactly as the verifier checks. */
    public static String sign(String secret, Instant signedAt, String rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (signedAt + "." + (rawPayload == null ? "" : rawPayload)).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 unavailable", unavailable);
        }
    }

    private FacilitiesException rejected(VendorInboxPort.InboxEntry entry, VendorRejectionReason reason,
            String detail, String module, ActorContext actor) {
        // Platform work: the sender's scope is exactly what cannot be trusted, and a scoped principal
        // forging another site's traffic must still leave a record. See PlatformThreads.callAsPlatform.
        PlatformThreads.callAsPlatform(() -> rejections.record(entry, reason, detail, module, actor));
        return new FacilitiesException(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED);
    }

    private static VendorInboxPort.InboxEntry withSite(VendorInboxPort.InboxEntry entry, String site) {
        return new VendorInboxPort.InboxEntry(entry.sourceId(), entry.channel(), entry.messageType(),
                entry.idempotencyKey(), site, entry.payloadHash(), entry.signedAt(), entry.receivedAt(),
                entry.correlationId());
    }

    private static String normalise(String sourceId) {
        return sourceId == null || sourceId.isBlank() ? null : sourceId.strip().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
