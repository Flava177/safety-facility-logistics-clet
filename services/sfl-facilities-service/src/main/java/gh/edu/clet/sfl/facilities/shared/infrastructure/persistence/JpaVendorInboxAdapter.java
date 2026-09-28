package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorInboxPort;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link VendorInboxPort} over JPA.
 *
 * <p>{@code saveAndFlush} on an accepted row, so a concurrent delivery of the same key trips
 * {@code ux_vendor_inbox_accepted} inside the claiming transaction rather than at commit, after the
 * domain action has already run once.
 */
@Component
class JpaVendorInboxAdapter implements VendorInboxPort {

    private final VendorInboxMessageRepository repository;

    JpaVendorInboxAdapter(VendorInboxMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean alreadyAccepted(String sourceId, String idempotencyKey) {
        return idempotencyKey != null && repository.existsAccepted(sourceId, idempotencyKey);
    }

    @Override
    public UUID recordAccepted(InboxEntry entry) {
        return repository.saveAndFlush(entity(entry, "ACCEPTED", null, null)).id();
    }

    @Override
    public UUID recordDuplicate(InboxEntry entry) {
        return repository.save(entity(entry, "DUPLICATE", null, null)).id();
    }

    @Override
    public UUID recordRejected(InboxEntry entry, VendorRejectionReason reason, String detail) {
        return repository.save(entity(entry, "REJECTED", reason, detail)).id();
    }

    private static VendorInboxMessageEntity entity(InboxEntry entry, String outcome, VendorRejectionReason reason,
            String detail) {
        return new VendorInboxMessageEntity(UUID.randomUUID(), entry.sourceId(), entry.channel(),
                entry.messageType(), entry.idempotencyKey(), entry.siteCode(), outcome, reason, detail,
                entry.payloadHash(), entry.signedAt(), entry.receivedAt(), entry.correlationId());
    }
}
