package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a vendor-message rejection permanent before the refusal is thrown.
 *
 * <h2>Why a transaction of its own</h2>
 *
 * <p>NFR-SEC2: forged or malformed messages are "rejected and logged, never actioned". The refusal is an
 * exception, and an exception rolls back the transaction it is thrown in - taking the inbox row, the
 * audit record and the outbox event with it if they were written there. The result would be a service
 * that rejected forged telemetry perfectly and kept no record of it: the one thing an investigation
 * needs. {@code REQUIRES_NEW} commits all four before the caller's transaction is abandoned.
 *
 * <p>A separate bean rather than a method on the verifier, because Spring's transaction proxy does not
 * intercept a call a bean makes to itself.
 *
 * <h2>Four records, one fact</h2>
 * <ul>
 *   <li>the inbox row - what arrived and why it was refused, queryable by channel;</li>
 *   <li>{@code VENDOR_MESSAGE_REJECTED} on the hash chain (S204) - tamper-evident;</li>
 *   <li>the SIEM forward (S208) - a forged message is a security event, not a data-quality one;</li>
 *   <li>{@code sfl.integration.vendor-message-rejected.v1} - the catalogue's pre-reserved name.</li>
 * </ul>
 */
@Service
public class VendorRejectionRecorder {

    private final VendorInboxPort inbox;
    private final AuditPort audit;
    private final SecurityEventForwarderPort siem;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public VendorRejectionRecorder(VendorInboxPort inbox, AuditPort audit, SecurityEventForwarderPort siem,
            ServiceOutbox outbox, Clock clock) {
        this.inbox = inbox;
        this.audit = audit;
        this.siem = siem;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(VendorInboxPort.InboxEntry entry, VendorRejectionReason reason, String detail,
            String module, ActorContext actor) {
        UUID id = inbox.recordRejected(entry, reason, detail);
        String site = entry.siteCode() == null ? "*" : entry.siteCode();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("inboxId", id.toString());
        facts.put("sourceSystem", entry.sourceId());
        facts.put("channel", entry.channel().name());
        facts.put("reason", reason.name());
        facts.put("messageType", entry.messageType());
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.VENDOR_MESSAGE_REJECTED, "VendorInboxMessage",
                id.toString(), site, null, facts);
        siem.forward(new SecurityEvent(module, "VENDOR_MESSAGE_REJECTED",
                reason == VendorRejectionReason.SCHEMA_INVALID ? SecurityEvent.Severity.WARNING
                        : SecurityEvent.Severity.HIGH,
                entry.siteCode(), "Vendor message rejected on " + entry.channel() + ": " + reason,
                id.toString(), clock.instant()));
        outbox.record("sfl.integration.vendor-message-rejected.v1", 1, "VendorInboxMessage", id, site,
                actor.correlationId(), actor.actorId(), facts);
        return id;
    }
}
