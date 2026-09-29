package gh.edu.clet.sfl.facilities.buildingsystems;

import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorInboxPort;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorSourcePort;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A minimal {@link VendorSourcePort} / {@link VendorInboxPort} pair for S156 tests, mirroring what
 * {@code ConfiguredVendorSources} and {@code JpaVendorInboxAdapter} do in production - real checks, no
 * database.
 */
public final class InMemoryVendorSupport {

    private InMemoryVendorSupport() {
    }

    public static final String SOURCE_ID = "BMS-SIM";
    public static final String SECRET = "test-bms-secret";

    static VendorSourcePort sources() {
        return sourceId -> SOURCE_ID.equalsIgnoreCase(sourceId)
                ? Optional.of(new VendorSourcePort.VendorSource(SOURCE_ID, VendorChannel.BMS_TELEMETRY, SECRET,
                        Set.of("MAIN", "KSI")))
                : Optional.empty();
    }

    /** An in-memory inbox that enforces the same idempotency rule as the real one, for a replay test. */
    static final class InMemoryVendorInbox implements VendorInboxPort {

        private final Set<String> accepted = new HashSet<>();
        private final Map<String, VendorRejectionReason> rejections = new LinkedHashMap<>();

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
            rejections.put(entry.sourceId() + "|" + entry.idempotencyKey(), reason);
            return UUID.randomUUID();
        }

        Map<String, VendorRejectionReason> rejections() {
            return Map.copyOf(rejections);
        }
    }
}
