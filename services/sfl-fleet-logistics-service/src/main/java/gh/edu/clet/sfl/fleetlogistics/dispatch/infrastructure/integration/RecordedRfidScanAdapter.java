package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.RfidScanPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-2 RFID seam. The Phase-1 recorded adapter records the scan reference only; a real RFID reader
 * integration replaces this class without any domain change, so sealed-material RFID reads can later
 * attach to the custody record.
 */
@Component
public class RecordedRfidScanAdapter implements RfidScanPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedRfidScanAdapter.class);

    @Override
    public void recordRfidScan(UUID dispatchId, String tagId, Instant occurredAt) {
        log.info("Recorded RFID scan dispatch={} tagId={} occurredAt={}", dispatchId, tagId, occurredAt);
    }
}
