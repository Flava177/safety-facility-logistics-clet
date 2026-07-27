package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase-2 RFID seam. Provider-neutral; the Phase-1 recorded adapter records the scan reference only.
 * Present so sealed-material RFID reads can later attach to custody without a domain change.
 */
public interface RfidScanPort {

    void recordRfidScan(UUID dispatchId, String tagId, Instant occurredAt);
}
