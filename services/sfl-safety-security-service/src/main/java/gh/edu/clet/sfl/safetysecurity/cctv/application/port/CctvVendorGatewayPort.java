package gh.edu.clet.sfl.safetysecurity.cctv.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import java.time.Instant;

/**
 * Retrieves/exports footage from the purchased VMS/NVR for an approved evidence request - S161's
 * Buy-and-Integrate half (SFL owns the request and its approval, the vendor system owns the actual
 * clip). No vendor is chosen yet; see {@code RecordedCctvVendorGateway}, which mirrors S160a's
 * {@code RecordedAccessControlVendorGateway}.
 */
public interface CctvVendorGatewayPort {

    RetrievalResult retrieve(EvidenceRequest approvedRequest, String cameraId, Instant windowStart, Instant windowEnd,
            ActorContext actor);

    /** @param exportHandle the VMS's own reference/handle for the retrieved clip.
     *  @param hash a cryptographic hash computed by the vendor gateway at export time. */
    record RetrievalResult(String provider, boolean retrieved, String exportHandle, String hash, String provenance) {
    }
}
