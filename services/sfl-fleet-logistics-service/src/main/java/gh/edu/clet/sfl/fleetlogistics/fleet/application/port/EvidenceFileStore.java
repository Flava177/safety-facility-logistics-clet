package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Where evidence bytes live.
 *
 * <p>A port rather than a repository call, because the storage answer is expected to change and the
 * callers are not. Release 1 keeps the bytes in the service's own schema; a deployment with an object
 * store swaps in an adapter that writes to it and hands back a signed URL, and nothing above this
 * interface has to know which of those happened.
 *
 * <p>Note what is <em>not</em> here: no delete. Evidence retention is decided against the metadata
 * record and its retention class, and the file row is removed with it by the database. A capability
 * to delete content on its own would be a capability to make a legal hold meaningless.
 */
public interface EvidenceFileStore {

    /** The bytes, with just enough about them to serve a response. */
    record StoredFile(UUID evidenceId, byte[] content, String contentType, long byteSize) {
    }

    /**
     * Stores content for an already-registered evidence reference.
     *
     * <p>Called inside the same transaction as the registration, so an upload is either metadata plus
     * bytes or neither. A reference with no bytes is exactly the pre-Release-1 state this replaced,
     * and reintroducing it one row at a time is how a file store rots.
     */
    void store(UUID evidenceId, byte[] content, String contentType, String scanStatus, String scanDetail,
            Instant scannedAt, String uploadedBy);

    Optional<StoredFile> find(UUID evidenceId);

    /** Whether bytes exist, without reading them. Used by list responses to offer a download link. */
    boolean exists(UUID evidenceId);

    /**
     * Which of these records have bytes, in one query.
     *
     * <p>The single-record {@link #exists} would turn a list of twenty evidence rows into twenty
     * round trips for a boolean that decides whether to draw a download button. Mixed lists are the
     * norm rather than the exception - anything registered before the file store has metadata only -
     * so this is asked on every evidence list there is.
     */
    java.util.Set<UUID> withContent(java.util.Collection<UUID> evidenceIds);
}
