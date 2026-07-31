package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AuditChainStateRepository extends JpaRepository<AuditChainStateEntity, Short> {

    /**
     * Claims the chain head for writing.
     *
     * <p>{@code PESSIMISTIC_WRITE} is what serialises appends. It is one row, held for the duration of
     * one command's transaction — brief, and the only way the sequence number and the previous-hash
     * link stay consistent under concurrency. Without it two writers both read sequence <em>n</em>:
     * one fails on the unique constraint and the other commits a record whose predecessor never
     * existed, which replays later as tampering that never happened.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuditChainStateEntity s where s.id = 1")
    Optional<AuditChainStateEntity> lockHead();
}
