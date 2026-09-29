package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventHandoffRepository extends JpaRepository<EventHandoffRecord, UUID> {

    @Query("""
            select h from EventHandoffRecord h
            where h.sourceSystem = :source and h.idempotencyKey = :idempotencyKey
              and h.outcome = gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff.Outcome.ACCEPTED
            """)
    Optional<EventHandoffRecord> findAccepted(@Param("source") String source,
            @Param("idempotencyKey") String idempotencyKey);

    @Query("select h from EventHandoffRecord h where h.s078EventReference = :reference order by h.receivedAt asc")
    List<EventHandoffRecord> findForReference(@Param("reference") String reference);
}
