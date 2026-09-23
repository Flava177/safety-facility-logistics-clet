package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded CCTV/VMS export. No VMS is configured, so it honestly reports {@code retrieved =
 * false} rather than fabricating a real export, while still producing a stable handle and hash so the
 * evidence-by-reference workflow (SRS-SFL-S161-03) can be exercised end to end. Mirrors S160a's
 * {@code RecordedAccessControlVendorGateway}; a real adapter replaces this without any domain change.
 */
@Component
public class RecordedCctvVendorGateway implements CctvVendorGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedCctvVendorGateway.class);

    @Override
    public RetrievalResult retrieve(EvidenceRequest approvedRequest, String cameraId, Instant windowStart,
            Instant windowEnd, ActorContext actor) {
        log.info("Recorded CCTV retrieval request={} camera={} window={}..{} - no VMS provider configured",
                approvedRequest.id(), cameraId, windowStart, windowEnd);
        String handle = "RECORDED-" + UUID.randomUUID();
        String provenance = "Recorded stand-in export: no VMS provider is configured for site "
                + approvedRequest.siteCode() + ".";
        String hash = sha256(approvedRequest.id() + "|" + cameraId + "|" + windowStart + "|" + windowEnd + "|"
                + handle);
        return new RetrievalResult("NONE-RECORDED", false, handle, hash, provenance);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
